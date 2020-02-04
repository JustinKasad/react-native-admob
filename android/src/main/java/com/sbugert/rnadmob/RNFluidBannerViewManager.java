package com.sbugert.rnadmob;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.view.View;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableNativeArray;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableNativeMap;
import com.facebook.react.uimanager.PixelUtil;
import com.facebook.react.uimanager.ViewGroupManager;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import com.facebook.react.views.view.ReactViewGroup;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.doubleclick.AppEventListener;
import com.google.android.gms.ads.doubleclick.PublisherAdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.doubleclick.PublisherAdView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import android.util.Log;
import android.view.WindowManager;
import android.widget.LinearLayout;

class ReactFluidAdView extends LinearLayout implements AppEventListener {

    protected PublisherAdView adView;
    protected ReactFluidAdView _self;

    String[] testDevices;
    ReadableMap customTargeting;
    AdSize[] validAdSizes;
    String adUnitID;
    AdSize adSize;
    Integer adWidth;
    Integer adHeight;

    public ReactFluidAdView(final Context context) {
        super(context);
        this.createAdView();
    }

    private void createAdView() {
        if (this.adView != null) this.adView.destroy();

        final Context context = getContext();
        this.adView = new PublisherAdView(context);
        this.adView.setAppEventListener(this);
        _self = this;

        this.adView.setAdListener(new AdListener() {
            @Override
            public void onAdLoaded() {
                int width = adView.getAdSize().getWidthInPixels(context);
                int height = adView.getAdSize().getHeightInPixels(context);
                int left = adView.getLeft();
                int top = adView.getTop();
                adView.measure(width, height);
                adView.layout(left, top, left + width, top + height);
                sendOnSizeChangeEvent();
                sendEvent(RNFluidBannerViewManager.EVENT_AD_LOADED, null);

                new android.os.Handler().postDelayed(
                        new Runnable() {
                            public void run() {
                                DisplayMetrics dm = new DisplayMetrics();

                                WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                                if (windowManager != null) {
                                    windowManager.getDefaultDisplay().getMetrics(dm);
                                }

                                float density = dm.density;
                                int height = (int) (_self.adHeight * density);
                                int width =  (int) (_self.adWidth * density);
                                int left = adView.getLeft();
                                int top = adView.getTop();
                                adView.measure(width, height);
                                adView.layout(left, top, left + width, top + height);
                            }
                        },
                        100);
            }

            @Override
            public void onAdFailedToLoad(int errorCode) {
                String errorMessage = "Unknown error";
                switch (errorCode) {
                    case PublisherAdRequest.ERROR_CODE_INTERNAL_ERROR:
                        errorMessage = "Internal error, an invalid response was received from the ad server.";
                        break;
                    case PublisherAdRequest.ERROR_CODE_INVALID_REQUEST:
                        errorMessage = "Invalid ad request, possibly an incorrect ad unit ID was given.";
                        break;
                    case PublisherAdRequest.ERROR_CODE_NETWORK_ERROR:
                        errorMessage = "The ad request was unsuccessful due to network connectivity.";
                        break;
                    case PublisherAdRequest.ERROR_CODE_NO_FILL:
                        errorMessage = "The ad request was successful, but no ad was returned due to lack of ad inventory.";
                        break;
                }
                WritableMap event = Arguments.createMap();
                WritableMap error = Arguments.createMap();
                error.putString("message", errorMessage);
                event.putMap("error", error);
                sendEvent(RNFluidBannerViewManager.EVENT_AD_FAILED_TO_LOAD, event);
            }

            @Override
            public void onAdOpened() {
                sendEvent(RNFluidBannerViewManager.EVENT_AD_OPENED, null);
            }

            @Override
            public void onAdClosed() {
                sendEvent(RNFluidBannerViewManager.EVENT_AD_CLOSED, null);
            }

            @Override
            public void onAdLeftApplication() {
                sendEvent(RNFluidBannerViewManager.EVENT_AD_LEFT_APPLICATION, null);
            }
        });
        this.addView(this.adView);
    }

    private void sendOnSizeChangeEvent() {
        int width;
        int height;
        ReactContext reactContext = (ReactContext) getContext();
        WritableMap event = Arguments.createMap();
        AdSize adSize = this.adView.getAdSize();
        if (adSize == AdSize.SMART_BANNER) {
            width = (int) PixelUtil.toDIPFromPixel(adSize.getWidthInPixels(reactContext));
            height = (int) PixelUtil.toDIPFromPixel(adSize.getHeightInPixels(reactContext));
        } else if(this.adSize == AdSize.FLUID) {
            width = (int) this.adWidth;
            height = (int) this.adHeight;
        } else {
            width = adSize.getWidth();
            height = adSize.getHeight();
        }
        event.putDouble("width", width);
        event.putDouble("height", height);
        sendEvent(RNFluidBannerViewManager.EVENT_SIZE_CHANGE, event);
    }

    private void sendEvent(String name, @Nullable WritableMap event) {
        ReactContext reactContext = (ReactContext) getContext();
        reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
                        getId(),
                        name,
                        event);
    }

    public void loadBanner() {
        ArrayList<AdSize> adSizes = new ArrayList<AdSize>();
        if (this.adSize != null) {
            adSizes.add(this.adSize);
        }
        if (this.validAdSizes != null) {
            for (int i = 0; i < this.validAdSizes.length; i++) {
                adSizes.add(this.validAdSizes[i]);
            }
        }

        if (adSizes.size() == 0) {
            adSizes.add(AdSize.BANNER);
        }

        AdSize[] adSizesArray = adSizes.toArray(new AdSize[adSizes.size()]);

        this.adView.setAdSizes(adSizesArray);

        PublisherAdRequest.Builder adRequestBuilder = new PublisherAdRequest.Builder();
        if (testDevices != null) {
            for (int i = 0; i < testDevices.length; i++) {
                String testDevice = testDevices[i];
                if (testDevice == "SIMULATOR") {
                    testDevice = PublisherAdRequest.DEVICE_ID_EMULATOR;
                }
                adRequestBuilder.addTestDevice(testDevice);
            }
        }

        if(customTargeting != null) {
            HashMap map = ((ReadableNativeMap) customTargeting).toHashMap();
            Iterator<Map.Entry<String, ArrayList>> iterator = map.entrySet().iterator() ;
            while(iterator.hasNext()){
                Map.Entry<String, ArrayList> val = iterator.next();
                adRequestBuilder.addCustomTargeting(val.getKey(), val.getValue());
            }
        }

        PublisherAdRequest adRequest = adRequestBuilder.build();
        this.adView.loadAd(adRequest);
    }

    public void setAdUnitID(String adUnitID) {
        if (this.adUnitID != null) {
            // We can only set adUnitID once, so when it was previously set we have
            // to recreate the view
            this.createAdView();
        }
        this.adUnitID = adUnitID;
        this.adView.setAdUnitId(adUnitID);
    }

    public void setTestDevices(String[] testDevices) {
        this.testDevices = testDevices;
    }

    public void setCustomTargeting(ReadableMap customTargeting) { this.customTargeting = customTargeting; }

    public void setAdSize(AdSize adSize) {
        this.adSize = adSize;
    }

    public void setAdWidth(Integer width) {
        this.adWidth = width;
    }

    public void setAdHeight(Integer height) {
        this.adHeight = height;
    }

    public void setValidAdSizes(AdSize[] adSizes) {
        this.validAdSizes = adSizes;
    }

    @Override
    public void onAppEvent(String name, String info) {
        WritableMap event = Arguments.createMap();
        event.putString("name", name);
        event.putString("info", info);
        sendEvent(RNFluidBannerViewManager.EVENT_APP_EVENT, event);
    }
}

public class RNFluidBannerViewManager extends ViewGroupManager<ReactFluidAdView> {

    public static final String REACT_CLASS = "RNFluidBannerView";

    public static final String PROP_AD_SIZE = "adSize";
    public static final String PROP_AD_WIDTH = "adWidth";
    public static final String PROP_AD_HEIGHT = "adHeight";
    public static final String PROP_VALID_AD_SIZES = "validAdSizes";
    public static final String PROP_AD_UNIT_ID = "adUnitID";
    public static final String PROP_TEST_DEVICES = "testDevices";
    public static final String PROP_CUSTOM_TARGETING = "customTargeting";

    public static final String EVENT_SIZE_CHANGE = "onSizeChange";
    public static final String EVENT_AD_LOADED = "onAdLoaded";
    public static final String EVENT_AD_FAILED_TO_LOAD = "onAdFailedToLoad";
    public static final String EVENT_AD_OPENED = "onAdOpened";
    public static final String EVENT_AD_CLOSED = "onAdClosed";
    public static final String EVENT_AD_LEFT_APPLICATION = "onAdLeftApplication";
    public static final String EVENT_APP_EVENT = "onAppEvent";

    public static final int nativeAdHeight = 82;

    public static final int COMMAND_LOAD_BANNER = 1;

    @Override
    public String getName() {
        return REACT_CLASS;
    }

    @Override
    protected ReactFluidAdView createViewInstance(ThemedReactContext themedReactContext) {
        ReactFluidAdView adView = new ReactFluidAdView(themedReactContext);
        return adView;
    }

    @Override
    public void addView(ReactFluidAdView parent, View child, int index) {
        throw new RuntimeException("RNFluidBannerView cannot have subviews");
    }

    @Override
    @Nullable
    public Map<String, Object> getExportedCustomDirectEventTypeConstants() {
        MapBuilder.Builder<String, Object> builder = MapBuilder.builder();
        String[] events = {
            EVENT_SIZE_CHANGE,
            EVENT_AD_LOADED,
            EVENT_AD_FAILED_TO_LOAD,
            EVENT_AD_OPENED,
            EVENT_AD_CLOSED,
            EVENT_AD_LEFT_APPLICATION,
            EVENT_APP_EVENT
        };
        for (int i = 0; i < events.length; i++) {
            builder.put(events[i], MapBuilder.of("registrationName", events[i]));
        }
        return builder.build();
    }

    @ReactProp(name = PROP_AD_SIZE)
    public void setPropAdSize(final ReactFluidAdView view, final String sizeString) {
        AdSize adSize;
        if(isNumeric(sizeString)){
            adSize = new AdSize(Integer.parseInt(sizeString), nativeAdHeight);;
        } else {
            adSize = getAdSizeFromString(sizeString);
        }
	    view.setAdSize(adSize);
    }

    @ReactProp(name = PROP_AD_WIDTH)
    public void setPropAdWidth(final ReactFluidAdView view, final Integer width) {
        view.setAdWidth(width);
    }

    @ReactProp(name = PROP_AD_HEIGHT)
    public void setPropAdHeight(final ReactFluidAdView view, final Integer height) {
        view.setAdHeight(height);
    }

    public boolean isNumeric(String s) {
        return s != null && s.matches("[-+]?\\d*\\.?\\d+");
    }

    @ReactProp(name = PROP_VALID_AD_SIZES)
    public void setPropValidAdSizes(final ReactFluidAdView view, final ReadableArray adSizeStrings) {
        ReadableNativeArray nativeArray = (ReadableNativeArray)adSizeStrings;
        ArrayList<Object> list = nativeArray.toArrayList();
        String[] adSizeStringsArray = list.toArray(new String[list.size()]);
        AdSize[] adSizes = new AdSize[list.size()];

        for (int i = 0; i < adSizeStringsArray.length; i++) {
                String adSizeString = adSizeStringsArray[i];
                adSizes[i] = getAdSizeFromString(adSizeString);
        }
        view.setValidAdSizes(adSizes);
    }

    @ReactProp(name = PROP_AD_UNIT_ID)
    public void setPropAdUnitID(final ReactFluidAdView view, final String adUnitID) {
        view.setAdUnitID(adUnitID);
    }

    @ReactProp(name = PROP_TEST_DEVICES)
    public void setPropTestDevices(final ReactFluidAdView view, final ReadableArray testDevices) {
        ReadableNativeArray nativeArray = (ReadableNativeArray)testDevices;
        ArrayList<Object> list = nativeArray.toArrayList();
        view.setTestDevices(list.toArray(new String[list.size()]));
    }

    @ReactProp(name = PROP_CUSTOM_TARGETING)
    public void setPropCustomTargeting(final ReactFluidAdView view, final ReadableMap customTargeting) {
        view.setCustomTargeting(customTargeting);
    }

    private AdSize getAdSizeFromString(String adSize) {
        switch (adSize) {
            case "banner":
                return AdSize.BANNER;
            case "largeBanner":
                return AdSize.LARGE_BANNER;
            case "mediumRectangle":
                return AdSize.MEDIUM_RECTANGLE;
            case "fullBanner":
                return AdSize.FULL_BANNER;
            case "leaderBoard":
                return AdSize.LEADERBOARD;
            case "smartBannerPortrait":
                return AdSize.SMART_BANNER;
            case "smartBannerLandscape":
                return AdSize.SMART_BANNER;
            case "smartBanner":
                return AdSize.SMART_BANNER;
            case "fluid":
                return AdSize.FLUID;
            default:
                return AdSize.BANNER;
        }
    }

    @Nullable
    @Override
    public Map<String, Integer> getCommandsMap() {
        return MapBuilder.of("loadBanner", COMMAND_LOAD_BANNER);
    }

    @Override
    public void receiveCommand(ReactFluidAdView root, int commandId, @javax.annotation.Nullable ReadableArray args) {
        switch (commandId) {
            case COMMAND_LOAD_BANNER:
                root.loadBanner();
                break;
        }
    }
}
