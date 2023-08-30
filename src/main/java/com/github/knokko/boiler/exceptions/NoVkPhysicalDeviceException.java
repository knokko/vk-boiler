package com.github.knokko.boiler.exceptions;

public class NoVkPhysicalDeviceException extends RuntimeException {

    public NoVkPhysicalDeviceException() {
        super("No physical device satisfied all the requirements");
    }
}
