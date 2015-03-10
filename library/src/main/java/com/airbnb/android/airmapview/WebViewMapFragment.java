package com.airbnb.android.airmapview;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;

import com.airbnb.android.airmapview.listeners.InfoWindowCreator;
import com.airbnb.android.airmapview.listeners.OnCameraChangeListener;
import com.airbnb.android.airmapview.listeners.OnInfoWindowClickListener;
import com.airbnb.android.airmapview.listeners.OnMapBoundsCallback;
import com.airbnb.android.airmapview.listeners.OnMapClickListener;
import com.airbnb.android.airmapview.listeners.OnMapLoadedListener;
import com.airbnb.android.airmapview.listeners.OnMapMarkerClickListener;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public abstract class WebViewMapFragment extends Fragment implements AirMapInterface {

    private static final String TAG = WebViewMapFragment.class.getSimpleName();

    private WebView mWebView;
    private ViewGroup mLayout;
    private OnMapClickListener mOnMapClickListener;
    private OnCameraChangeListener mOnCameraChangeListener;
    private OnMapLoadedListener mOnMapLoadedListener;
    private OnMapMarkerClickListener mOnMarkerClickListener;
    private OnInfoWindowClickListener mOnInfoWindowClickListener;
    private InfoWindowCreator mInfoWindowCreator;
    private OnMapBoundsCallback mMapBoundsCallback;
    private LatLng mCenter;
    private int mZoom;
    private boolean mLoaded;
    private boolean mIgnoreNextMapMove;
    private View mInfoWindowView;

    public WebViewMapFragment setArguments(AirMapType mapType) {
        setArguments(mapType.toBundle());
        return this;
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_webview, container, false);

        mWebView = (WebView) view.findViewById(R.id.webview);
        mLayout = (ViewGroup) view;

        WebSettings webViewSettings = mWebView.getSettings();
        webViewSettings.setSupportZoom(true);
        webViewSettings.setBuiltInZoomControls(false);
        webViewSettings.setJavaScriptEnabled(true);

        AirMapType mapType = AirMapType.fromBundle(getArguments());

        mWebView.loadDataWithBaseURL(mapType.getDomain(), mapType.getMapData(getResources()),
                "text/html", "base64", null);
        mWebView.addJavascriptInterface(new MapsJavaScriptInterface(), "AirMapView");

        return view;
    }

    public int getZoom() {
        return mZoom;
    }

    public LatLng getCenter() {
        return mCenter;
    }

    public void setCenter(LatLng latLng) {
        mWebView.loadUrl(String.format("javascript:centerMap(%1$f, %2$f);", latLng.latitude,
                latLng.longitude));
    }

    public void animateCenter(LatLng latLng) {
        setCenter(latLng);
    }

    public void setZoom(int zoom) {
        mWebView.loadUrl(String.format("javascript:setZoom(%1$d);", zoom));
    }

    public void drawCircle(LatLng latLng, int radius) {
        drawCircle(latLng, radius, CIRCLE_BORDER_COLOR);
    }

    @Override
    public void drawCircle(LatLng latLng, int radius, int borderColor) {
        drawCircle(latLng, radius, borderColor, CIRCLE_BORDER_WIDTH);
    }

    @Override
    public void drawCircle(LatLng latLng, int radius, int borderColor, int borderWidth) {
        drawCircle(latLng, radius, borderColor, borderWidth, CIRCLE_FILL_COLOR);
    }

    @Override
    public void drawCircle(LatLng latLng, int radius, int borderColor, int borderWidth, int fillColor) {
        mWebView.loadUrl(String.format("javascript:addCircle(%1$f, %2$f, %3$d, %4$d, %5$d, %6$d);",
                latLng.latitude, latLng.longitude, radius, borderColor, borderWidth, fillColor));
    }

    public void highlightMarker(long markerId) {
        if (markerId != -1) {
            mWebView.loadUrl(String.format("javascript:highlightMarker(%1$d);", markerId));
        }
    }

    public void unhighlightMarker(long markerId) {
        if (markerId != -1) {
            mWebView.loadUrl(String.format("javascript:unhighlightMarker(%1$d);", markerId));
        }
    }

    @Override
    public boolean isInitialized() {
        return mWebView != null && mLoaded;
    }

    @Override
    public void addMarker(AirMapMarker marker) {
        LatLng latLng = marker.getLatLng();
        mWebView.loadUrl(String.format("javascript:addMarkerWithId(%1$f, %2$f, %3$d);",
                latLng.latitude, latLng.longitude, marker.getId()));
    }

    @Override
    public void removeMarker(AirMapMarker marker) {
        mWebView.loadUrl(String.format("javascript:removeMarker(%1$d);", marker.getId()));
    }

    public void clearMarkers() {
        mWebView.loadUrl("javascript:clearMarkers();");
    }

    public void setOnCameraChangeListener(OnCameraChangeListener listener) {
        mOnCameraChangeListener = listener;
    }

    public void setOnMapLoadedListener(OnMapLoadedListener listener) {
        mOnMapLoadedListener = listener;
        if (mLoaded) {
            mOnMapLoadedListener.onMapLoaded();
        }
    }

    @Override
    public void setCenterZoom(LatLng latLng, int zoom) {
        setCenter(latLng);
        setZoom(zoom);
    }

    @Override
    public void animateCenterZoom(LatLng latLng, int zoom) {
        setCenterZoom(latLng, zoom);
    }

    public void setOnMarkerClickListener(OnMapMarkerClickListener listener) {
        mOnMarkerClickListener = listener;
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        // no-op
    }

    @Override
    public void setMyLocationEnabled(boolean b) {
        // no-op
    }

    @Override
    public void addPolyline(AirMapPolyline polyline) {
        try {
            JSONArray array = new JSONArray();
            for (LatLng point : (List<LatLng>) polyline.getPoints()) {
                JSONObject json = new JSONObject();
                json.put("lat", point.latitude);
                json.put("lng", point.longitude);
                array.put(json);
            }

            mWebView.loadUrl(String.format(
                    "javascript:addPolyline(" + array.toString() + ", %1$d, %2$d, %3$d);",
                    polyline.getId(), polyline.getStrokeWidth(), polyline.getStrokeColor()));
        } catch (JSONException e) {
            Log.e(TAG, "error constructing polyline JSON", e);
        }
    }

    @Override
    public void removePolyline(AirMapPolyline polyline) {
        mWebView.loadUrl(String.format("javascript:removePolyline(%1$d);", polyline.getId()));
    }

    @Override
    public void setOnMapClickListener(final OnMapClickListener listener) {
        mOnMapClickListener = listener;
    }

    public void setOnInfoWindowClickListener(OnInfoWindowClickListener listener) {
        mOnInfoWindowClickListener = listener;
    }

    @Override
    public void setInfoWindowCreator(GoogleMap.InfoWindowAdapter adapter,
                                     InfoWindowCreator creator) {
        mInfoWindowCreator = creator;
    }

    public void getMapScreenBounds(OnMapBoundsCallback callback) {
        mMapBoundsCallback = callback;
        mWebView.loadUrl("javascript:getBounds();");
    }

    @Override
    public void setCenter(LatLngBounds bounds, int boundsPadding) {
        mWebView.loadUrl(String.format("javascript:setBounds(%1$f, %2$f, %3$f, %4$f);",
                bounds.northeast.latitude, bounds.northeast.longitude, bounds.southwest.latitude,
                bounds.southwest.longitude));
    }

    private class MapsJavaScriptInterface {

        private final Handler handler = new Handler(Looper.getMainLooper());

        @JavascriptInterface
        public void onMapLoaded() {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (!mLoaded) {
                        mLoaded = true;
                        if (mOnMapLoadedListener != null) {
                            mOnMapLoadedListener.onMapLoaded();
                        }
                    }
                }
            });
        }

        @JavascriptInterface
        public void mapClick(final double lat, final double lng) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (mOnMapClickListener != null) {
                        mOnMapClickListener.onMapClick(new LatLng(lat, lng));
                    }
                    if (mInfoWindowView != null) {
                        mLayout.removeView(mInfoWindowView);
                    }
                }
            });
        }

        @JavascriptInterface
        public void getBoundsCallback(double neLat, double neLng, double swLat, double swLng) {
            final LatLngBounds bounds = new LatLngBounds(new LatLng(swLat, swLng),
                    new LatLng(neLat, neLng));
            handler.post(new Runnable() {
                @Override
                public void run() {
                    mMapBoundsCallback.onMapBoundsReady(bounds);
                }
            });
        }

        @JavascriptInterface
        public void mapMove(double lat, double lng, int zoom) {
            mCenter = new LatLng(lat, lng);
            mZoom = zoom;

            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (mOnCameraChangeListener != null) {
                        mOnCameraChangeListener.onCameraChanged(mCenter, mZoom);
                    }

                    if (mIgnoreNextMapMove) {
                        mIgnoreNextMapMove = false;
                        return;
                    }

                    if (mInfoWindowView != null) {
                        mLayout.removeView(mInfoWindowView);
                    }
                }
            });
        }

        @JavascriptInterface
        public void markerClick(final long markerId) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (mOnMarkerClickListener != null) {
                        mOnMarkerClickListener.onMapMarkerClick(markerId);
                    }

                    if (mInfoWindowView != null) {
                        mLayout.removeView(mInfoWindowView);
                    }

                    // TODO convert to custom dialog fragment
                    if (mInfoWindowCreator != null) {
                        mInfoWindowView = mInfoWindowCreator.createInfoWindow(markerId);
                        int height = (int) getResources().getDimension(R.dimen.map_marker_height);
                        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.WRAP_CONTENT, height, Gravity.CENTER);
                        layoutParams.bottomMargin = height;
                        mInfoWindowView.setLayoutParams(layoutParams);
                        mLayout.addView(mInfoWindowView);

                        mInfoWindowView.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(@NonNull View v) {
                                mLayout.removeView(mInfoWindowView);
                                if (mOnInfoWindowClickListener != null) {
                                    mOnInfoWindowClickListener.onInfoWindowClick(markerId);
                                }
                            }
                        });
                    }

                    mIgnoreNextMapMove = true;
                }
            });
        }
    }
}