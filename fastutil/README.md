# Minifying fastutil
## Development instructions
vk-boiler uses fastutil for some primitive collection classes.
To keep the dependency small, we only take the ones that we actually need.
The classes that we need are stored in fastutil-mini.jar. To 'refresh' it,
run `./gradlew fastutil`.
