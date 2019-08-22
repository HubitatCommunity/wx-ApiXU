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
 
 public static String version()      {  return "v1.5.3"  }

/***********************************************************************************************************************
 *
 *
 * Version: 1.5.3
 *                Corrected typing on illuminance to Integer.
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
	//	command "updateCheck"			// **---** delete for Release
	//	command "pollApixu"			// **---** delete for Release
	}

      preferences 
      {
		input "apixuKey",      "text", title:"ApiXU key?", description: "<br><i>Leave blank for no Cloud compensation.</i><p>", required:true, defaultValue:null
		input "luxEvery",      "enum", title:"Publish illuminance how frequently?", required:false, defaultValue: 5, options:[5:"5 minutes",10:"10 minutes",15:"15 minutes",30:"30 minutes"]
		input "lowLuxEvery",   "enum", title:"When illuminance is minimum, how frequently is it published?", required:false, defaultValue: 999, options:[999: "don't change", 5:"5 minutes",10:"10 minutes",15:"15 minutes",30:"30 minutes"]

    	 // standard logging options
		input name: "debugOutput",    type: "bool", title: "Enable debug logging", defaultValue: true
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
	pollEvery = luxEvery
	"runEvery${pollEvery}Minutes"(pollApixu) 
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
		state.luxNext ? { schedule("0 0/${luxEvery} * * * ?", updateLux) } : {if (lowLuxEvery != 999) { schedule("0 0/${lowLuxEvery} * * * ?", updateLux) } }

		sendEvent(name: "illuminance", value: lux.toInteger(), unit: "lux")
		sendEvent(name: "illuminated", value: String.format("%,d lux", lux))
		sendEvent(name: "betwixt",     value: bwn)
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
	// log.debug "EpochMillis: now: $lT -> localMillis: $localeMillis, $sunRiseSet.civil_twilight_begin -> $twilight_beginMillis, Rise: $sunriseTimeMillis, Noon: $sunRiseSet.solar_noon -> $noonTimeMillis, Set: $sunsetTimeMillis, $sunRiseSet.civil_twilight_end -> $twilight_endMillis"

	if (   localeMillis > twilight_beginMillis   && localeMillis < sunriseTimeMillis)  {
		bwn = "between twilight and sunrise" 
		l = (((localeMillis - twilight_beginMillis) * 50f) / (sunriseTimeMillis - twilight_beginMillis))
		lux = (l < 10f ? 10l : l.trunc(0) as long)
	}
	else if (localeMillis > sunriseTimeMillis    && localeMillis < noonTimeMillis )    { 
		bwn = "between sunrise and noon" 
		l = (((localeMillis - sunriseTimeMillis) * 10000f) / (noonTimeMillis - sunriseTimeMillis))
		lux = (l < 50f ? 50l : l.trunc(0) as long)
	}
	else if (localeMillis > noonTimeMillis       && localeMillis < sunsetTimeMillis)   {
		bwn = "between noon and sunset" 
		l = (((sunsetTimeMillis - localeMillis) * 10000f) / (sunsetTimeMillis - noonTimeMillis))
		lux = (l < 50f ? 50l : l.trunc(0) as long)
	}
	else if (localeMillis > sunsetTimeMillis     && localeMillis < twilight_endMillis) {
		bwn = "between sunset and twilight" 
		l = (((twilight_endMillis - localeMillis) * 50f) / (twilight_endMillis - sunsetTimeMillis))
		lux = (l < 10f ? 10l : l.trunc(0) as long)
	}
	else {
		bwn = "Fully Night Time" 
		lux = 5l
		aFCC = false
	}

	// factor in cloud cover if available
	cCF = state.apixu.init ? ((100 - (cloud.toInteger() / 3d)) / 100) : 0.998d
   	// log.debug "zzz: $l $cloud $cCF, $lux, $bwn, $state.apixu.init"
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
		//if (debugOutput) log.debug "sunRiseSet: $state.sunRiseSet"
		state.localSunrise = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", sunRiseSet.sunrise).format("HH:mm")
		state.localSunset  = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", sunRiseSet.sunset).format("HH:mm")
		state.twiBegin     = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", sunRiseSet.civil_twilight_begin).format("HH:mm")
		state.twiEnd       = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", sunRiseSet.civil_twilight_end).format("HH:mm")
	} else { log.error "Sunrise-sunset api did not return data: $resp" }
}


/*
	poll

	Purpose: initiate the asynchtttpGet() call each poll cycle.

	Notes: very, very simple, all the action is in the handler.
*/
def pollApixu() {
	state?.apixu?.init = false
	if (apixuKey) {
		def requestParams = [ uri: "https://api.apixu.com/v1/current.json?key=$apixuKey&q=$location.latitude,$location.longitude&days=3" ]
		if (descTextEnable) log.info "Luxuriant-ApiXU poll for Cloud Data " //: $requestParams"
		asynchttpGet("apixuHandler", requestParams)
	} else { if (descTextEnable) log.info "Luxuriant-ApiXU no Key - no offset of Lux by cloud cover." }
}

/*
	pollHandler

	Purpose: the APIXU website response

	Notes: a good response will be processed by doPoll()
*/
def apixuHandler(resp, data) {
	if(resp.getStatus() == 200 || resp.getStatus() == 207) {
		obs = parseJson(resp.data)
        	if (debugOutput) log.debug "Luxuriant-ApiXU returned: $obs"
		state?.apixu?.init = true
		state.cloud = obs.current.cloud
	} else {
		log.error "Luxuriant-ApiXU weather api did not return data: $resp"
		state?.apixu?.init = false
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
	log.trace "Msg: installed ran"
}



/*
	initialize
    
*/
def initialize()
{
	unschedule()
	schedule("23 10 0 ? * * *", pollSunRiseSet)
	schedule("0 0 8 ? * FRI *", updateCheck)
	schedule("0 0/${luxEvery} * * * ?", updateLux)
	state.tz_id = TimeZone.getDefault().getID()
	if (state?.sunRiseSet?.init == null) state.sunRiseSet = [init:false]
	if (state?.apixu?.init      == null) state.apixu      = [init:false]
	runIn(4, updateLux) // give sunrise/set time to complete.
	runIn(20, updateCheck) 
	log.trace "Msg: initialize ran"
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
	
	def paramsUD = [uri: "https://hubitatcommunity.github.io/wx-ApiXU/version.json"]
	
 	asynchttpGet("updateCheckHandler", paramsUD) 
}

def updateCheckHandler(resp, data) {

	state.InternalName = "Luxuriant-Driver"

	if (resp.getStatus() == 200 || resp.getStatus() == 207) {
		respUD = parseJson(resp.data)
		// log.warn " Version Checking - Response Data: $respUD"   // Troubleshooting Debug Code - Uncommenting this line should show the JSON response from your webserver 
		state.Copyright = "${thisCopyright}"
		def newVerRaw = (respUD.versions.Driver.(state.InternalName))
		def newVer = (respUD.versions.Driver.(state.InternalName).replaceAll("[.vV]", ""))
		def currentVer = version().replaceAll("[.vV]", "")   
		state.UpdateInfo = (respUD.versions.UpdateInfo.Driver.(state.InternalName))
	
		if(newVer == "NLS")
		{
		      state.Status = "<b>** This driver is no longer supported by $respUD.author  **</b>"       
		      log.warn "** This driver is no longer supported by $respUD.author **"      
		}           
		else if(currentVer < newVer)
		{
		      state.Status = "<b>New Version Available (Version: $newVerRaw)</b>"
		      log.warn "** There is a newer version of this driver available  (Version: $newVerRaw) **"
		      log.warn "** $state.UpdateInfo **"
		} 
		else if(currentVer > newVer)
		{
		      state.Status = "<b>You are using a Test version of this Driver (Expecting: $newVerRaw)</b>"
		}
		else
		{ 
		    state.Status = "Current"
		    if (descTextEnable) log.info "You are using the current version of this driver"
		}
	
	      if(state.Status == "Current")
	      {
	           state.UpdateInfo = "N/A"
	           sendEvent(name: "DriverUpdate", value: state.UpdateInfo)
	           sendEvent(name: "DriverStatus", value: state.Status)
	      }
	      else 
	      {
	           sendEvent(name: "DriverUpdate", value: state.UpdateInfo)
	           sendEvent(name: "DriverStatus", value: state.Status)
	      }
      }
      else
      {
           log.error "Something went wrong: CHECK THE JSON FILE AND IT'S URI"
      }
}

def getThisCopyright(){"&copy; 2019 C Steele "}
