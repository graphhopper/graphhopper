# Translation

You can help improve GraphHopper by adding your language!

See [this spreadsheet](https://docs.google.com/spreadsheet/ccc?key=0AmukcXek0JP6dGM4R1VTV2d3TkRSUFVQakhVeVBQRHc#gid=0)
and add a column for your language. Revisit it regularly to update or add new items.

## Questions

 1. **What does the string after the language name mean ala 'Spanish: es'?**
    This is the language its ISO code (639-1) with two characters. E.g. look up your language on wikipedia to get this code. 
    See [Spanish](http://en.wikipedia.org/wiki/Spanish_language) as an example again.
 2. **What does the strange characters ala '%1$s' in the items means?**
    This is a placeholder which is filled by GraphHopper. It is important to have as in some languages the position
    is different than in other languages or the translation is completely different. 
    Example: "Enter roundabout and use exit %1$s". In German language you have to add the word 'nehmen' after the
    exit-number parameter: "In den Kreisverkehr einfahren und Ausfahrt %1$s nehmen". 
    It is very important that you do not forget about these parameter placeholders, please ask if you are unsure.

## Integrate into GraphHopper

We'll regularly update GraphHopper with new translations or fixes. To speed this process up or just
try if the changes work you can do the following:

 * Make GraphHopper working on your computer - see [here](./quickstart-from-source.md)
 * Download the document as 'tab-separated values'
 * Add your new language to the script: core/files/update-translations.sh
 * Execute ./core/files/update-translations.sh GraphHopper.tsv
 * 'git diff' and 'git status' should show your changes
 * Make sure 'mvn clean test' works without a problem. 
 * You can start a simple GraphHopper instance via './graphhopper.sh web europe_germany_berlin.pbf' and go to localhost:8989
 * Now please read the [contributing guide](https://github.com/graphhopper/graphhopper/blob/master/CONTRIBUTING.md) to submit your changes

## License Agreement

Please sign the <a href="http://www.clahub.com/agreements/graphhopper/graphhopper">GraphHopper License Agreement</a>.