## Reproduce validation layer false positive
**Note: a driver with swapchain maintenance 1 is required to
reproduce this presumed false positive.**

### Steps:
- Clone this project
- Install JDK 17
  - Should be `sudo apt install openjdk-17-jdk` on Ubuntu
  - You can obtain download it
    [here](https://adoptium.net/temurin/releases/?os=windows&version=17)
    for Windows.
  - Later versions of Java are allowed, earlier versions are not
- Run `java -jar reproduce.jar`
  - It will enable the api-dump layer by default. 
    To run without api dump, use `java -jar reproduce.jar dont-dump`
  - It will throw an exception upon encountering validation errors by default.
    To continue after encountering validation errors, use
    `java -jar reproduce.jar dont-throw`
  - It will prefer discrete GPUs by default. 
    To use an integrated GPU instead,
    use `java -jar reproduce.jar force-igpu`
- Close the window to cause the validation error.

### Building from source
- (Make some changes to the source code.)
- Run `./gradlew shadowJar`
- Run `java -jar samples/build/libs/samples-all.jar`
  - Again, you can use `dont-dump` and/or `dont-throw` and/or `force-igpu`

### Interesting code
- `src/main/java/com/github/knokko/boiler/sync/FenceBank.java`
  lines ~70 to ~80
- `src/main/java/com/github/knokko/boiler/swapchain/BoilerSwapchains.java`
  lines ~102 to ~111
