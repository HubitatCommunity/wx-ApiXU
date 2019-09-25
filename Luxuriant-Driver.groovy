/*
 * Import URL: https://raw.githubusercontent.com/HubitatCommunity/wx-ApiXU/master/Luxuriant-Driver.groovy
 *
 *	Copyright 2019 C Steele
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *	use this file except in compliance with the License. You may obtain a copy
 *	of the License at:
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software
 *	distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *	WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *	License for the specific language governing permissions and limitations
 *	under the License.
 *
 *
 *  attribution: sunrise and sunset courtesy: https://sunrise-sunset.org/
 *             : lux calc courtesy of Bengali
 *
 *  for use with HUBITAT, so no tiles
 */
 
 public static String version()      {  return "v1.6.0"  }

/***********************************************************************************************************************
 *
 *
 * Version: 1.6.0
 *                Added DarkSky integration
 *
 * Version: 1.5.6
 *                Added local_Sunrise and local_Sunset attributes because they're "free".
 *
 * Version: 1.5.5
 *                Improved updateCheck() with Switch/Case.
 *
 * Version: 1.5.4
 *			Increased Lux 'slices of a day' to include the next day.
 *                Increased pollSunRiseSet to every 8 hours (3 times a day). 
 *
 * Version: 1.5.3
 *                Corrected illuminance to Integer type.
 *
 * Version: 1.5.2
 *                Moved the subscribe statements to initialize().
 *                Reworked updateLux() to use a schedule() vs chained runIn for robustness.
 *                Moved sunRiseSet map from 'state' to 'data' storage.
 *
 * Version: 1.5.1
 *                Corrected typos on twilight (astro vs civil).
 *
 * Version: 1.5
 *                Added ApiXU call to get cloud data.
 *                Added attribute betwixt for Dashboard.
 *                Rewrote Lux calculation using Milliseconds vs Date Object
 *                 to be half the number of conversions.
 *
 * Version: 1.4
 *                Added attribute illuminated for Dashboard.
 *
 * Version: 1.3
 *                Added auto off on Debug logs
 *
 * Version: 1.2
 *                correction to updateCheck url.
 * 
 * Version: 1.1
 *                Initial Publish.
*/
 
import groovy.transform.Field

metadata 
{
	definition(name: "Luxuriant-Driver", namespace: "csteele", author: "C Steele", importUrl: "https://raw.githubusercontent.com/HubitatCommunity/wx-ApiXU/master/Luxuriant-Driver.groovy")
	{
 		capability "Illuminance Measurement"
 		capability "Polling"
 		capability "Sensor"

		attribute "illuminated",   "string"
		attribute "betwixt",       "string"
		attribute "local_sunrise", "string"
		attribute "local_sunset",  "string"

		command "pollSunRiseSet"
	//	command "pollForClouds"			/// **---** delete for Release
	//	command "updateCheck"			/// **---** delete for Release
	}

      preferences 
      {
		input "weatherKey",    "text", title:"<b>Weather key?</b>", description: "<br><i>Leave blank for no Cloud compensation.</i><p>", required:true, defaultValue:null
		input "whichSite",     "enum", title:"<b>Which Weather Site?</b>", required:false, defaultValue: false, options:[false: "use ApiXU", true: "use DarkSky"]
		input "luxEvery",      "enum", title:"<b>Publish illuminance how frequently?</b>", required:false, defaultValue: 5, options:[5:"5 minutes",10:"10 minutes",15:"15 minutes",30:"30 minutes"]
		input "lowLuxEvery",   "enum", title:"<b>When illuminance is minimum, how frequently is it published</b>?", required:false, defaultValue: 999, options:[999: "don't change", 5:"5 minutes",10:"10 minutes",15:"15 minutes",30:"30 minutes"]

    	 // standard logging options
		input name: "debugOutput",    type: "bool", title: "<b>Enable debug logging</b>", defaultValue: true
		input name: "descTextEnable", type: "bool", title: "<b>Enable descriptionText logging?</b>", defaultValue: true
      }
}


// helpers
def poll()	{ updateLux() }


/*
	updated
    
	Doesn't do much other than call initialize().
*/
def updated()
{
	initialize()
	if (debugOutput) runIn(1800,logsOff)        // disable debug logs after 30 min
	if (descTextEnable) log.info "Updated with settings: ${settings}, $state.tz_id, $state.sunRiseSet"
	pollSunRiseSet
	if (whichSite == "true") { state.wxClouds.name = "Luxuriant-DarkSky"; state.wxClouds.is = true } else { state.wxClouds.name = "Luxuriant-Apixu"; state.wxClouds.is = false }
	if (weatherKey) schedule("3 0/${luxEvery} * * * ?", pollForClouds)
}


/*
	updateLux

	Purpose: calculate Lux / Illuminance / Illuminated offset by time of day
	
	Notes: minimum Lux is a value of 5 after dark.
*/
def updateLux()     {
	if (state?.sunRiseSet?.init) { 
		if (descTextEnable) log.info "Luxurient lux calc for: $location.latitude  $location.longitude"	
		def (lux, bwn) = estimateLux(state.condition_code, state.cloud)
		state.luxNext = (lux > 6) ? true : false 
		state.luxNext ? { schedule("7 0/${luxEvery} * * * ?", updateLux) } : {if (lowLuxEvery != 999) { schedule("0 0/${lowLuxEvery} * * * ?", updateLux) } }

		sendEvent(name: "illuminance", value: lux.toInteger(), unit: "lux")
		sendEvent(name: "illuminated", value: String.format("%,d lux", lux))
		sendEvent(name: "betwixt",     value: bwn)

		sendEvent(name: "local_sunrise", value: state.localSunrise)
		sendEvent(name: "local_sunset",  value: state.localSunset)
        	if (debugOutput) log.debug "Lux: $lux, $state.luxNext, $bwn"
	} else {
		if (descTextEnable) log.info "no Luxurient lux without sunRiseSet value."
		pollSunRiseSet()
	}
}


def estimateLux(condition_code, cloud)     {	
	def lux = 0l
	def aFCC = true
	def l
	def bwn

	def sunRiseSet           = parseJson(getDataValue("sunRiseSet")).results
	def tZ                   = TimeZone.getTimeZone(state.tz_id)
	def lT                   = new Date().format("yyyy-MM-dd'T'HH:mm:ssXXX", tZ)
	def localeMillis         = getEpoch(lT)
	def sunriseTimeMillis    = getEpoch(sunRiseSet.sunrise)
	def sunsetTimeMillis     = getEpoch(sunRiseSet.sunset)
	def noonTimeMillis       = getEpoch(sunRiseSet.solar_noon)
	def twilight_beginMillis = getEpoch(sunRiseSet.civil_twilight_begin)
	def twilight_endMillis   = getEpoch(sunRiseSet.civil_twilight_end)
	def twiStartNextMillis   = twilight_beginMillis + 86400000 // -->24*60*60*1000
	def sunriseNextMillis    = sunriseTimeMillis + 86400000 // -->24*60*60*1000
	def noonTimeNextMillis   = noonTimeMillis + 86400000 // -->24*60*60*1000
	def sunsetNextMillis     = sunsetTimeMillis + 86400000 // -->24*60*60*1000
	def twiEndNextMillis     = twilight_endMillis + 86400000 // -->24*60*60*1000

	switch(localeMillis) { 
		case { it < twilight_beginMillis}: 
			bwn = "Fully Night Time" 
			lux = 5l
			break
		case { it < sunriseTimeMillis}:
			bwn = "between twilight and sunrise" 
			l = (((localeMillis - twilight_beginMillis) * 50f) / (sunriseTimeMillis - twilight_beginMillis))
			lux = (l < 10f ? 10l : l.trunc(0) as long)
			break
		case { it < noonTimeMillis}:
			bwn = "between sunrise and noon" 
			l = (((localeMillis - sunriseTimeMillis) * 10000f) / (noonTimeMillis - sunriseTimeMillis))
			lux = (l < 50f ? 50l : l.trunc(0) as long)
			break
		case { it < sunsetTimeMillis}:
			bwn = "between noon and sunset" 
			l = (((sunsetTimeMillis - localeMillis) * 10000f) / (sunsetTimeMillis - noonTimeMillis))
			lux = (l < 50f ? 50l : l.trunc(0) as long)
			break
		case { it < twilight_endMillis}:
			bwn = "between sunset and twilight" 
			l = (((twilight_endMillis - localeMillis) * 50f) / (twilight_endMillis - sunsetTimeMillis))
			lux = (l < 10f ? 10l : l.trunc(0) as long)
			break
		case { it < twiStartNextMillis}:
			bwn = "Fully Night Time" 
			lux = 5l
			break
		case { it < sunriseNextMillis}:
			bwn = "between twilight and sunrise" 
			l = (((localeMillis - twiStartNextMillis) * 50f) / (sunriseNextMillis - twiStartNextMillis))
			lux = (l < 10f ? 10l : l.trunc(0) as long)
			break
		case { it < noonTimeNextMillis}:
			bwn = "between sunrise and noon" 
			l = (((localeMillis - sunriseNextMillis) * 10000f) / (noonTimeNextMillis - sunriseNextMillis))
			lux = (l < 50f ? 50l : l.trunc(0) as long)
			break
		case { it < sunsetNextMillis}:
			bwn = "between noon and sunset" 
			l = (((sunsetNextMillis - localeMillis) * 10000f) / (sunsetNextMillis - noonTimeNextMillis))
			lux = (l < 50f ? 50l : l.trunc(0) as long)
			break
		case { it < twiEndNextMillis}:
			bwn = "between sunset and twilight" 
			l = (((twiEndNextMillis - localeMillis) * 50f) / (twiEndNextMillis - sunsetNextMillis))
			lux = (l < 10f ? 10l : l.trunc(0) as long)
			break
		default:
			bwn = "Fully Night Time" 
			lux = 5l
			aFCC = false
			break
	}

	// factor in cloud cover if available
	cCF = state?.wxClouds?.init ? ((100 - (cloud.toInteger() / 3d)) / 100) : 0.998d
 	lux = (lux * cCF) as long
	
	return [lux, bwn]
}


/*
	Sun Rise Set

	Purpose: Run just after midnight to establish the Astronomical times needed all day long when polling APIXU.

*/
def pollSunRiseSet() {
	if (true) {
		def requestParams = [ uri: "https://api.sunrise-sunset.org/json?lat=$location.latitude&lng=$location.longitude&formatted=0" ]
		if (descTextEnable) log.info "SunRiseSet poll for $location.latitude  $location.longitude " //: $requestParams"
		asynchttpGet("sunRiseSetHandler", requestParams)
	} else {
		state?.sunRiseSet?.init = false
		log.error "No sunrise-sunset without Lat/Long."
	}
}


def sunRiseSetHandler(resp, data) {
	if(resp.getStatus() == 200 || resp.getStatus() == 207) {
		sunRiseSet = resp.getJson().results
        	updateDataValue("sunRiseSet", resp.getData())
		state?.sunRiseSet?.init = true
		//if (debugOutput) log.debug "sunRiseSet: $sunRiseSet"
		state.localSunrise = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", sunRiseSet.sunrise).format("HH:mm")
		state.localSunset  = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", sunRiseSet.sunset).format("HH:mm")
		state.twiBegin     = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", sunRiseSet.civil_twilight_begin).format("HH:mm")
		state.twiEnd       = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", sunRiseSet.civil_twilight_end).format("HH:mm")
	} else { log.error "Sunrise-sunset api did not return data" }
}


/*
	pollForClouds

	Purpose: initiate the asynchtttpGet() call each poll cycle.

	Notes: very, very simple, all the action is in the handler.
*/
def pollForClouds() {
	state?.wxClouds?.init = false
	def requestParams = ''
	if (weatherKey) {
		if (state.wxClouds.is) { requestParams = [ uri: "https://api.darksky.net/forecast/${weatherKey}/" + location.latitude + ',' + location.longitude + "?units=us&exclude=minutely,hourly,flags" ] } else { requestParams = [ uri: "https://api.apixu.com/v1/current.json?key=$weatherKey&q=$location.latitude,$location.longitude&days=3" ] }
		if (descTextEnable) log.info "$state.wxClouds.name poll for Cloud Data : $requestParams"
		asynchttpGet("forCloudsHandler", requestParams)
	} else { if (descTextEnable) log.info "$state.wxClouds.name no Key - no offset of Lux by cloud cover." }
}


/*
	forCloudsHandler

	Purpose: the WX site's response

*/
def forCloudsHandler(resp, data) {
	if(resp.getStatus() == 200 || resp.getStatus() == 207) {
		obs = parseJson(resp.data)
      //  	if (debugOutput) log.debug "$state.wxClouds.name returned: $obs"
		state?.wxClouds?.init = true
		if (state.wxClouds.is) {state.cloud = obs.currently.cloudCover} else {state.cloud = obs.current.cloud}
		sendEvent(name: "cloud", value: state.cloud.toBigDecimal(), unit: "%")
	} else {
		log.error "$state.wxClouds.name weather api did not return data"
		state?.wxClouds?.init = false
	}
}



/*
	getEpoch

	Purpose: take a Date object and return Milliseconds (Epoch)

	Notes:
*/
def getEpoch (aTime) {
	def tZZ = TimeZone.getTimeZone(state.tz_id)
	def localeTime = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", aTime, tZZ)
	long localeMillis = localeTime.getTime()
	return (localeMillis)
}


/*

	generic driver stuff

*/


/*
	installed
    
	Doesn't do much other than call initialize().
*/
def installed()
{
	initialize()
	log.trace "Installed ran"
}



/*
	initialize
    
*/
def initialize()
{
	unschedule()
	schedule("17 20 0/8 ? * * *", pollSunRiseSet)
	schedule("0 0 8 ? * FRI *", updateCheck)
	schedule("17 0/${luxEvery} * * * ?", updateLux)
	state.tz_id = TimeZone.getDefault().getID()
	//	state.remove("sunRiseSet") // converted to 'data' storage, no longer need 'state' storage.
	//	state.remove("lowLuxRepeat") // using schedule(), no longer need 'state' storage.
	if (state?.sunRiseSet?.init == null) state.sunRiseSet  = [init:false]
	if (state?.wxClouds?.init   == null) state.wxClouds = [init:false]
	runIn(4, updateLux) // give sunrise/set time to complete.
	runIn(20, updateCheck) 
	state.DarkSky = "<a href='https://darksky.net/poweredby/'>Powered by Dark Sky</a>"
	log.trace "Initialize ran"
}


/*
	logsOff

	Purpose: automatically disable debug logging after 30 mins.

*/
def logsOff(){
	log.warn "debug logging disabled..."
	device.updateSetting("debugOutput",[value:"false",type:"bool"])
}



// Check Version   ***** with great thanks and acknowlegment to Cobra (CobraVmax) for his original code ****
def updateCheck()
{
	def paramsUD = [uri: "https://hubitatcommunity.github.io/wx-ApiXU/version2.json"]
	
 	asynchttpGet("updateCheckHandler", paramsUD) 
}

def updateCheckHandler(resp, data) {

	state.InternalName = "Luxuriant-Driver"

	if (resp.getStatus() == 200 || resp.getStatus() == 207) {
		respUD = parseJson(resp.data)
		// if (debugOutput) log.debug " Version Checking - Response Data: $respUD"   // Troubleshooting Debug Code - Uncommenting this line should show the JSON response from your webserver 
		state.Copyright = "${thisCopyright} -- ${version()}"
		// uses reformattted 'version2.json' 
		def newVer = padVer(respUD.driver.(state.InternalName).ver)
		def currentVer = padVer(version())               
		state.UpdateInfo = (respUD.driver.(state.InternalName).updated)
            // if (debugOutput) log.debug "updateCheck: ${respUD.driver.(state.InternalName).ver}, $state.UpdateInfo, ${respUD.author}"

		switch(newVer) {
			case { it == "NLS"}:
			      state.Status = "<b>** This Driver is no longer supported by ${respUD.author}  **</b>"       
			      if (descTextEnable) log.warn "** This Driver is no longer supported by ${respUD.author} **"      
				break
			case { it > currentVer}:
			      state.Status = "<b>New Version Available (Version: ${respUD.driver.(state.InternalName).ver})</b>"
			      if (descTextEnable) log.warn "** There is a newer version of this Driver available  (Version: ${respUD.driver.(state.InternalName).ver}) **"
			      if (descTextEnable) log.warn "** $state.UpdateInfo **"
				break
			case { it < currentVer}:
			      state.Status = "<b>You are using a Test version of this Driver (Expecting: ${respUD.driver.(state.InternalName).ver})</b>"
			      if (descTextEnable) log.warn "You are using a Test version of this Driver (Expecting: ${respUD.driver.(state.InternalName).ver})"
				break
			default:
				state.Status = "Current"
				if (descTextEnable) log.info "You are using the current version of this driver"
				break
		}

 	sendEvent(name: "chkUpdate", value: state.UpdateInfo)
	sendEvent(name: "chkStatus", value: state.Status)
      }
      else
      {
           log.error "Something went wrong: CHECK THE JSON FILE AND IT'S URI"
      }
}

/*
	padVer

	Version progression of 1.4.9 to 1.4.10 would mis-compare unless each column is padded into two-digits first.

*/ 
def padVer(ver) {
	def pad = ""
	ver.replaceAll( "[vV]", "" ).split( /\./ ).each { pad += it.padLeft( 2, '0' ) }
	return pad
}

def getThisCopyright(){"&copy; 2019 C Steele "}
