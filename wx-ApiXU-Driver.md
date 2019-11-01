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

