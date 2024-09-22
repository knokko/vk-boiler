Use `java -jar multiple-windows.jar` to run it. On my computer,
this generates plenty of validation errors per second.

Use `java -jar multiple-windows.jar dump` to run it with api
dump enabled. Note that you might want to use
`java -jar multiple-windows.jar dump > dump.txt` instead to
dump everything to a file.

I already generated `dump.txt`, `dump1.txt`, etc...

You can use `./gradlew shadowJar` to build it 'from source':
the output can then be found at 
`samples/build/libs/samples-all.jar`.