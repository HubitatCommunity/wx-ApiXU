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
 *
 *  for use with HUBITAT, so no tiles
 */
 
 public static String version()      {  return "v0.2"  }

/***********************************************************************************************************************
 *
 * Version: 0.1
 *                Initial Publish
*/
 
import groovy.transform.Field

metadata 
{
	definition(name: "Luxuriant-Driver", namespace: "csteele", author: "C Steele")
	{
 		capability "Illuminance Measurement"
 		capability "Polling"
 		capability "Sensor"
	}

      preferences 
      {
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
	schedule("23 10 0 ? * * *", pollSunRiseSet)
	schedule("0 0 8 ? * FRI *", updateCheck)
	state.tz_id = TimeZone.getDefault().getID()
	state.luxNext = luxEvery.toInteger() * 60	
	state.lowLuxRepeat = (lowLuxEvery == '999') ? luxEvery.toInteger() * 60 : lowLuxEvery.toInteger() * 60
	if (descTextEnable) log.info "Updated with settings: ${settings}, $state.tz_id, $state.sunRiseSet"
	pollSunRiseSet
	runIn(4, updateLux) // give sunrise/set time to complete.
}



/*
	updateLux

	Purpose: calculate Lux / Illuminance / Illuminated offset by time of day
	
	Notes: minimum Lux is a value of 5 after dark.
*/
def updateLux()     {
	runIn(state.luxNext, updateLux)
	if (state?.sunRiseSet?.init) { 
		if (descTextEnable) log.info "Luxurient lux calc for: $location.latitude  $location.longitude"	
		def lux = estimateLux(state.condition_code, state.cloud)
		sendEvent(name: "illuminance", value: lux.toFloat(), unit: "lux", displayed: true)
		sendEvent(name: "illuminated", value: String.format("%,d lux", lux), displayed: true)
		state.luxNext = (lux > 6) ? state.luxNext.toInteger() : state.lowLuxRepeat.toInteger()
        	if (debugOutput) log.debug "Lux: $lux, $state.luxNext"
	} else {
		if (descTextEnable) log.info "no Luxurient lux without sunRiseSet value."
		pollSunRiseSet()
	}
}


def estimateLux(condition_code, cloud)     {	
	def tZ              = TimeZone.getTimeZone(state.tz_id)
	def lT              = new Date().format("yyyy-MM-dd'T'HH:mm:ssXXX", tZ)
	def localTime       = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", lT, tZ)
	def sunriseTime     = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", state.sunRiseSet.sunrise, tZ)
	def sunsetTime      = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", state.sunRiseSet.sunset, tZ)
	def noonTime        = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", state.sunRiseSet.solar_noon, tZ)
	def twilight_begin  = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", state.sunRiseSet.civil_twilight_begin, tZ)
	def twilight_end    = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", state.sunRiseSet.civil_twilight_end, tZ)

	def lux = 0l
	def aFCC = true
	def l

	if (timeOfDayIsBetween(sunriseTime, noonTime, localTime))      {
		if (debugOutput) log.debug "between sunrise and noon"
		l = (((localTime.getTime() - sunriseTime.getTime()) * 10000f) / (noonTime.getTime() - sunriseTime.getTime()))
		lux = (l < 50f ? 50l : l.trunc(0) as long)
	}
	else if (timeOfDayIsBetween(noonTime, sunsetTime, localTime))      {
		if (debugOutput) log.debug "between noon and sunset"
		l = (((sunsetTime.getTime() - localTime.getTime()) * 10000f) / (sunsetTime.getTime() - noonTime.getTime()))
		lux = (l < 50f ? 50l : l.trunc(0) as long)
	}
	else if (timeOfDayIsBetween(twilight_begin, sunriseTime, localTime))      {
		if (debugOutput) log.debug "between sunrise and twilight"
		l = (((localTime.getTime() - twilight_begin.getTime()) * 50f) / (sunriseTime.getTime() - twilight_begin.getTime()))
		lux = (l < 10f ? 10l : l.trunc(0) as long)
	}
	else if (timeOfDayIsBetween(sunsetTime, twilight_end, localTime))      {
		if (debugOutput) log.debug "between sunset and twilight"
		l = (((twilight_end.getTime() - localTime.getTime()) * 50f) / (twilight_end.getTime() - sunsetTime.getTime()))
		lux = (l < 10f ? 10l : l.trunc(0) as long)
	}
	else if (!timeOfDayIsBetween(twilight_begin, twilight_end, localTime))      {
		if (debugOutput) log.debug "between non-twilight"
		lux = 5l
		aFCC = false
	}
	
	cCF = 1.0
	lux = (lux * cCF) as long
	
	return lux
}


/*
	Sun Rise Set

	Purpose: Run just after midnight to establish the Astronomical times needed all day long when polling APIXU.

*/
def pollSunRiseSet() {
	if (true) {
		def requestParams = [ uri: "https://api.sunrise-sunset.org/json?lat=$location.latitude&lng=$location.longitude&formatted=0" ]
		if (descTextEnable) log.info "SunRiseSet poll for $location.latitude  $location.longitude : $requestParams"
		asynchttpGet("sunRiseSetHandler", requestParams)
	} else {
		state.sunRiseSet.init = false
		log.error "No sunrise-sunset without Lat/Long."
	}
}


def sunRiseSetHandler(resp, data) {
	if(resp.getStatus() == 200 || resp.getStatus() == 207) {
		state.sunRiseSet = resp.getJson().results
		state.sunRiseSet.init = true
		//if (debugOutput) log.debug "sunRiseSet: $state.sunRiseSet"
		state.localSunrise = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", state.sunRiseSet.sunrise).format("HH:mm")
		state.localSunset  = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", state.sunRiseSet.sunset).format("HH:mm")
		state.twiBegin = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", state.sunRiseSet.astronomical_twilight_begin).format("HH:mm")
		state.twiEnd   = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", state.sunRiseSet.astronomical_twilight_end).format("HH:mm")
	} else {
		log.error "Sunrise-sunset api did not return data: $resp"
	}
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
    
	Doesn't do anything.
*/
def initialize()
{
	unschedule()
	log.trace "Msg: initialize ran"
}


/*
	parse
    
	In a virtual world this should never be called.
*/
def parse(String description)
{
	log.trace "Msg: Description is $description"
}

/*
	on
    
	Turns the device on.
*/
def on()
{
	// The server will update on/off status
	log.trace "Msg: $description ON"
	
}


/*
	off
    
	Turns the device off.
*/
def off()
{
	// The server will update on/off status
	log.trace "Msg: $description OFF"
}



// Check Version   ***** with great thanks and acknowlegment to Cobra (CobraVmax) for his original code ****
def updateCheck()
{    
	state.Version = version()
	state.InternalName = "Luxuriant-Driver"
	
	def paramsUD = [uri: "https://hubitatcommunity.github.io/wx-ApiXU-Driver/version.json"]
	
 	asynchttpGet("updateCheckHandler", paramsUD) 
}

def updateCheckHandler(resp, data) {
	if (resp.getStatus() == 200 || resp.getStatus() == 207) {
		respUD = parseJson(resp.data)
		//log.warn " Version Checking - Response Data: $respUD"   // Troubleshooting Debug Code - Uncommenting this line should show the JSON response from your webserver 
		state.Copyright = "${thisCopyright}"
		def newVerRaw = (respUD.versions.Driver.(state.InternalName))
		def newVer = (respUD.versions.Driver.(state.InternalName).replaceAll("[.vV]", ""))
		def currentVer = state.Version.replaceAll("[.vV]", "")                
		state.UpdateInfo = (respUD.versions.UpdateInfo.Driver.(state.InternalName))
		state.author = (respUD.author)
	
		if(newVer == "NLS")
		{
		      state.Status = "<b>** This driver is no longer supported by $state.author  **</b>"       
		      log.warn "** This driver is no longer supported by $state.author **"      
		}           
		else if(currentVer < newVer)
		{
		      state.Status = "<b>New Version Available (Version: $newVerRaw)</b>"
		      log.warn "** There is a newer version of this driver available  (Version: $newVerRaw) **"
		      log.warn "** $state.UpdateInfo **"
		} 
		else if(currentVer > newVer)
		{
		      state.Status = "<b>You are using a Test version of this Driver (Version: $newVerRaw)</b>"
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
