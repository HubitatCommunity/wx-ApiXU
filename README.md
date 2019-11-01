# Luxuriant
<p>
<h2> Hubitat Driver to calculates Lux / Illuminance attributea.</h2>
<p>
<b>-- == Release v1.6 == –</b>
<p>
Added support for DarkSky.net to acquire cloud cover values.
<p>
Luxuriant uses your Hub's Latitude, Longitude and Time/Time Zone to calculate a 'slice of day' and thus
the angle of the sun. Assuming a max Outdoor Lux of 10000 the sun's angle is factored in to produce a value taht would be roughly equivilant to a cloudless day. Optionally, adding a DarkSky key will allow for your local / regional cloud cover value to also be factored into the calculation.
  
<p>
<hr>
<p>
<hr>
wx-APIXU-Driver DEPRECATED  APIXU DEPRECATED  APIXU DEPRECATED  APIXU DEPRECATED 

wx-APIXU-Driver DEPRECATED

wx-APIXU-Driver Code no longer maintained.

APIXU was acquired by WeatherStack and no longer offers the Free resources this code was based upon.



wx-APIXU-Driver
<p>
Hubitat Driver to selectively acquire weather and attributes.
<p>
<b>-- == Release v1.3 == –</b>
<p>
There are Four main ‘loops’ in wx-ApiXU-Driver code:<br>
poll()<br>
updateLux()<br>
pollSunRiseSet()<br>
updateCheck()<br>
<p>
All have been converted to asynchttp to free the hub to do other things while the website (cloud) responds, and are completely independent. There are ‘road blocks’ between the ‘loops’ though. Everything starts with the value you enter in the first Preference: ‘Zip code or city name or latitude,longitude? \*’ That value is sent to ApiXU and returns with Latitude/Longitude and Time Zone. pollSunRiseSet() accepts only Latitude/Longitude + Time Zone and thus must defer til the results come back from ApiXU. UpdateLux() must wait for Sunrise, Sunset, Noon and Twilight times from pollSunRiseSet(). updateLux() was included as part of poll() originally, and that has been removed to make each loop as independent as possible.
<p>
<b>-- ==  Release v1.2  == --</b>
This release makes a big change in the way the Attributes are presented to the Hub. From Day One, Attributes have been strings. Wind Speed, Precipitation, and Visibility, to name but a few, are all sent from APIXU as numbers. Beginning with v1.2, wx-ApiXU-Driver submits value to the hub in their intended format. Numbers are numbers (Floating point or Integer) and text remains as Strings. Depending on how you’ve been using APIXU this past year, this change has the possibility of breaking your comparisons.<p>
  <b>Please take great care in upgrading to v1.2 and beyond.</b> Take the time to evaluate your use and to check the results. If you find the results are different, either roll back to v1.1.9 or adjust your automations to account for the new values.<p>
    <hr>
<p>This code was Cloned from Bangali's ApiXU Weather Driver (v5.4.1) and involves some cosmetic changes but mostly maintenance (support) improvements.<p>
  Install as Driver Code and then Create a Virtual Device that uses this driver. <br>
  Update with your postal code and Key, click Save Preferences.<p>
  Sunset and Sunrise calculations are run once per day, just after midnight, or when Save Preferences is clicked.<p>
<h3>Attributes available:</h3>
Cloud cover factor<br>
City<br>
Cloud<br>
Condition code<br>
Condition icon only<br>
Condition icon URL<br>
Condition icon<br>
Condition text<br>
Country<br>
Clock<br>
Feels like °C<br>
Feels like °F<br>
Feels like (in default unit)<br>
Forecast icon<br>
Humidity<br>
Illuminance<br>
Dashboard illuminance<br>
Is daytime<br>
Last updated epoch<br>
Last updated<br>
Latitude and Longitude<br>
Local date<br>
Local sunrise<br>
Local sunset<br>
Local time<br>
Localtime epoch<br>
Location name with region<br>
Mytile for dashboard<br>
Location name<br>
OpenWeather attributes<br>
Percent precipitation<br>
Extended Precipitation<br>
Precipitation Inches<br>
Precipitation MM<br>
Pressure<br>
Region<br>
Temperature<br>
Temperature high day +1<br>
Temperature low day +1<br>
Twilight begin<br>
Twilight end<br>
Timezone ID<br>
Visibility KM<br>
Visibility miles<br>
Visual weather<br>
Visual weather day +1<br>
Visual weather day +1 with text<br>
Visual weather with text<br>
Weather<br>
Wind Degree<br>
Wind direction<br>
Wind KPH<br>
Wind MPH<br>
Wind MPS<br>
Wind mytile<br>
Wind (in default unit)<br>



