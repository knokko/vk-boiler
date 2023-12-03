package com.github.knokko.boiler.xr;

import com.github.knokko.boiler.util.ReflectionHelper;
import org.lwjgl.openxr.XR10;

import static org.lwjgl.openxr.XR10.*;

public class OpenXrFailureException extends RuntimeException {

    public static void assertXrSuccess(int result, String functionName, String context, int... allowedResults) {
        if (result == XR_SUCCESS) return;
        for (int allowed : allowedResults) {
            if (result == allowed) return;
        }

        if (!functionName.startsWith("xr")) functionName = "xr" + functionName;
        throw new OpenXrFailureException(functionName, result, context);
    }

    static String generateMessage(String functionName, int result, String context) {

        // First try the constants starting with XR_ERROR_
        String returnCodeName = ReflectionHelper.getIntConstantName(
                XR10.class, result, "XR_ERROR_", "", "unknown"
        );

        // Unfortunately, the positive return codes don't start with XR_ERROR_, but may still be undesired. I'm
        // afraid checking them manually is the only right way to find them...
        if (returnCodeName.equals("unknown")) {
            switch (result) {
                case XR_TIMEOUT_EXPIRED -> returnCodeName = "XR_TIMEOUT_EXPIRED";
                case XR_SESSION_LOSS_PENDING -> returnCodeName = "XR_SESSION_LOSS_PENDING";
                case XR_EVENT_UNAVAILABLE -> returnCodeName = "XR_EVENT_UNAVAILABLE";
                case XR_SPACE_BOUNDS_UNAVAILABLE -> returnCodeName = "XR_SPACE_BOUNDS_UNAVAILABLE";
                case XR_SESSION_NOT_FOCUSED -> returnCodeName = "XR_SESSION_NOT_FOCUSED";
                case XR_FRAME_DISCARDED -> returnCodeName = "XR_FRAME_DISCARDED";
            }
        }

        String messageStart = functionName + " ";
        if (context != null) messageStart += "(" + context + ") ";
        return messageStart + "returned " + result + " (" + returnCodeName + ")";
    }

    public final String functionName;
    public final int result;
    public final String context;

    public OpenXrFailureException(String functionName, int result, String context) {
        super(generateMessage(functionName, result, context));
        this.functionName = functionName;
        this.result = result;
        this.context = context;
    }
}
