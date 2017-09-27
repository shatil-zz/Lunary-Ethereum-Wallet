package rehanced.com.simpleetherwallet;

/**
 * Created by Shahanur on 9/27/2017.
 */

public class AppSettings {
    private static final String BUILD_MODE_DEV = "dev", BUILD_MODE_LIVE = "live";
    private static final String APK_BUILD_MODE = BUILD_MODE_DEV;
    public static String BASE_URL;

    static {
        BASE_URL = getBaseUrl(APK_BUILD_MODE);
    }

    private static String getBaseUrl(String buildMode) {
        switch (buildMode) {
            case BUILD_MODE_DEV:
                return "https://ropsten.etherscan.io/";
            case BUILD_MODE_LIVE:
                return "https://api.etherscan.io/";
            default:
                return "https://etherscan.io/";
        }
    }
}
