package com.mparticle.internal;

import android.content.Context;
import android.content.SharedPreferences;

import com.mparticle.ExceptionHandler;
import com.mparticle.MParticle;
import com.mparticle.MParticle.LogLevel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class ConfigManager {
    public static final String CONFIG_JSON = "json";
    private static final String KEY_TRIGGER_ITEMS = "tri";
    private static final String KEY_MESSAGE_MATCHES = "mm";
    private static final String KEY_TRIGGER_ITEM_HASHES = "evts";
    private static final String KEY_INFLUENCE_OPEN = "pio";
    static final String KEY_OPT_OUT = "oo";
    public static final String KEY_UNHANDLED_EXCEPTIONS = "cue";
    public static final String KEY_PUSH_MESSAGES = "pmk";
    public static final String KEY_EMBEDDED_KITS = "eks";
    static final String KEY_UPLOAD_INTERVAL = "uitl";
    static final String KEY_SESSION_TIMEOUT = "stl";
    public static final String KEY_AAID_LAT = "rdlat";
    public static final String VALUE_APP_DEFINED = "appdefined";
    public static final String VALUE_CUE_CATCH = "forcecatch";
    private static final String PREFERENCES_FILE = "mp_preferences";
    private static final String KEY_INCLUDE_SESSION_HISTORY = "inhd";
    private static final String KEY_DEVICE_PERFORMANCE_METRICS_DISABLED = "dpmd";
    static final String KEY_RAMP = "rp";

    private static final int DEVMODE_UPLOAD_INTERVAL_MILLISECONDS = 10 * 1000;
    private Context mContext;

    static SharedPreferences mPreferences;

    AppConfig sLocalPrefs;
    private static JSONArray sPushKeys;
    private String mLogUnhandledExceptions = VALUE_APP_DEFINED;

    private boolean mSendOoEvents;
    private JSONObject mProviderPersistence;
    private boolean mRestrictAAIDfromLAT = true;
    private int mRampValue = -1;
    private int mUserBucket = -1;

    private int mSessionTimeoutInterval = -1;
    private int mUploadInterval = -1;
    private long mInfluenceOpenTimeout = 3600 * 1000;
    private JSONArray mTriggerMessageMatches, mTriggerMessageHashes = null;
    private ExceptionHandler mExHandler;
    private boolean mIncludeSessionHistory = true;
    private JSONObject mCurrentCookies;

    private ConfigManager(){

    }

    public ConfigManager(Context context, MParticle.Environment environment, String apiKey, String apiSecret) {
        mContext = context.getApplicationContext();
        mPreferences = mContext.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);
        sLocalPrefs = new AppConfig(mContext, environment, mPreferences, apiKey, apiSecret);
        restoreOldConfig();
    }

    private void restoreOldConfig() {
        String oldConfig = mPreferences.getString(CONFIG_JSON, null);
        if (!MPUtility.isEmpty(oldConfig)){
            try{
                JSONObject oldConfigJson = new JSONObject(oldConfig);
                updateConfig(oldConfigJson, false);
            }catch (Exception jse){

            }
        }
    }

    /**
     * The is called on startup. The only thing that's completely necessary is that we fire up kits.
     */
    public JSONArray getLatestKitConfiguration(){
        String oldConfig = mPreferences.getString(CONFIG_JSON, null);
        if (!MPUtility.isEmpty(oldConfig)){
            try{
                JSONObject oldConfigJson = new JSONObject(oldConfig);
                return oldConfigJson.optJSONArray(KEY_EMBEDDED_KITS);
            }catch (Exception jse){

            }
        }
        return null;
    }

    void saveConfigJson(JSONObject json){
        if (json != null) {
            mPreferences.edit().putString(CONFIG_JSON, json.toString()).apply();
        }
    }

    public synchronized void updateConfig(JSONObject responseJSON) throws JSONException {
        updateConfig(responseJSON, true);
    }
    public synchronized void updateConfig(JSONObject responseJSON, boolean newConfig) throws JSONException {
        SharedPreferences.Editor editor = mPreferences.edit();
        if (newConfig) {
            saveConfigJson(responseJSON);
        }

        if (responseJSON.has(KEY_UNHANDLED_EXCEPTIONS)) {
            mLogUnhandledExceptions = responseJSON.getString(KEY_UNHANDLED_EXCEPTIONS);
        }

        if (responseJSON.has(KEY_PUSH_MESSAGES) && newConfig) {
            sPushKeys = responseJSON.getJSONArray(KEY_PUSH_MESSAGES);
            editor.putString(KEY_PUSH_MESSAGES, sPushKeys.toString());
        }

        mRampValue = responseJSON.optInt(KEY_RAMP, -1);

        if (responseJSON.has(KEY_OPT_OUT)){
            mSendOoEvents = responseJSON.getBoolean(KEY_OPT_OUT);
        }else{
            mSendOoEvents = false;
        }

        if (responseJSON.has(ProviderPersistence.KEY_PERSISTENCE)) {
            setProviderPersistence(new ProviderPersistence(responseJSON, mContext));
        }else{
            setProviderPersistence(null);
        }

        mSessionTimeoutInterval = responseJSON.optInt(KEY_SESSION_TIMEOUT, -1);
        mUploadInterval = responseJSON.optInt(KEY_UPLOAD_INTERVAL, -1);

        mTriggerMessageMatches = null;
        mTriggerMessageHashes = null;
        if (responseJSON.has(KEY_TRIGGER_ITEMS)){
            try {
                JSONObject items = responseJSON.getJSONObject(KEY_TRIGGER_ITEMS);
                if (items.has(KEY_MESSAGE_MATCHES)){
                    mTriggerMessageMatches = items.getJSONArray(KEY_MESSAGE_MATCHES);
                }
                if (items.has(KEY_TRIGGER_ITEM_HASHES)){
                    mTriggerMessageHashes = items.getJSONArray(KEY_TRIGGER_ITEM_HASHES);
                }
            }catch (JSONException jse){

            }

        }

        if (responseJSON.has(KEY_INFLUENCE_OPEN)){
            mInfluenceOpenTimeout = responseJSON.getLong(KEY_INFLUENCE_OPEN) * 60 * 1000;
        }else{
            mInfluenceOpenTimeout = 30 * 60 * 1000;
        }

        mRestrictAAIDfromLAT = responseJSON.optBoolean(KEY_AAID_LAT, true);
        mIncludeSessionHistory = responseJSON.optBoolean(KEY_INCLUDE_SESSION_HISTORY, true);
        if (responseJSON.has(KEY_DEVICE_PERFORMANCE_METRICS_DISABLED)) {
            MParticle.setDevicePerformanceMetricsDisabled(
                    responseJSON.optBoolean(KEY_DEVICE_PERFORMANCE_METRICS_DISABLED, false)
            );
        }

        editor.apply();
        applyConfig();
        if (newConfig) {
            MParticle.getInstance().getKitManager().updateKits(responseJSON.optJSONArray(KEY_EMBEDDED_KITS));
        }
    }

    public String getActiveModuleIds(){
        return MParticle.getInstance().getKitManager().getActiveModuleIds();
    }

    public boolean getIncludeSessionHistory() {
        return mIncludeSessionHistory;
    }

    /**
     * Indicates if the Android Advertising ID should be collected regardless of the limit ad tracking
     * setting. Google allows the usage of AAID regardless of the LAT setting for cases of anonymous analytics,
     * attribution, etc. By default, this will return True, which means that that SDK should *not* collect AAID
     * when the user has enable limit ad tracking.
     *
     * @return true if AAID should only be send when LAT is disabled.
     */
    public boolean getRestrictAAIDBasedOnLAT() {
        return mRestrictAAIDfromLAT;
    }

    /**
     * When the Config manager starts up, we don't want to enable everything immediately to save on app-load time.
     * This method will be called from a background thread after startup is already complete.

     */
    public void delayedStart(){
        sLocalPrefs.delayedInit();
        if (isPushEnabled() && PushRegistrationHelper.getLatestPushRegistration(mContext) == null) {
            MParticle.getInstance().Messaging().enablePushNotifications(getPushSenderId());
        }
    }

    public JSONArray getTriggerMessageMatches(){
        return mTriggerMessageMatches;
    }

    public long getInfluenceOpenTimeoutMillis(){
        return mInfluenceOpenTimeout;
    }

    private void applyConfig() {
        if (getLogUnhandledExceptions()) {
            enableUncaughtExceptionLogging(false);
        } else {
            disableUncaughtExceptionLogging(false);
        }
    }

    public void enableUncaughtExceptionLogging(boolean userTriggered) {
        if (null == mExHandler) {
            Thread.UncaughtExceptionHandler currentUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
            if (!(currentUncaughtExceptionHandler instanceof ExceptionHandler)) {
                mExHandler = new ExceptionHandler(currentUncaughtExceptionHandler);
                Thread.setDefaultUncaughtExceptionHandler(mExHandler);
                if (userTriggered) {
                    setLogUnhandledExceptions(true);
                }
            }
        }
    }

    public void disableUncaughtExceptionLogging(boolean userTriggered) {
        if (null != mExHandler) {
            Thread.UncaughtExceptionHandler currentUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
            if (currentUncaughtExceptionHandler instanceof ExceptionHandler) {
                Thread.setDefaultUncaughtExceptionHandler(mExHandler.getOriginalExceptionHandler());
                mExHandler = null;
                if (userTriggered) {
                    setLogUnhandledExceptions(false);
                }
            }
        }
    }

    public boolean getLogUnhandledExceptions() {
        if (mLogUnhandledExceptions.equals(VALUE_APP_DEFINED)) {
            return sLocalPrefs.reportUncaughtExceptions;
        } else {
            return mLogUnhandledExceptions.equals(VALUE_CUE_CATCH);
        }
    }

    public void setLogUnhandledExceptions(boolean log){
        sLocalPrefs.reportUncaughtExceptions = log;
    }

    public String getApiKey() {
        return sLocalPrefs.mKey;
    }

    public String getApiSecret() {
        return sLocalPrefs.mSecret;
    }

    public long getUploadInterval() {
        if (getEnvironment().equals(MParticle.Environment.Development)) {
            return DEVMODE_UPLOAD_INTERVAL_MILLISECONDS;
        } else {
            if (mUploadInterval > 0){
                return 1000 * mUploadInterval;
            }else {
                return (1000 * sLocalPrefs.uploadInterval);
            }
        }
    }

    public static MParticle.Environment getEnvironment() {
        return AppConfig.getEnvironment();
    }

    public void setUploadInterval(int uploadInterval) {
        sLocalPrefs.uploadInterval = uploadInterval;
    }

    public int getSessionTimeout() {
        if (mSessionTimeoutInterval > 0){
            return mSessionTimeoutInterval * 1000;
        }else{
            return sLocalPrefs.sessionTimeout * 1000;
        }
    }

    public void setSessionTimeout(int sessionTimeout) {
        sLocalPrefs.sessionTimeout = sessionTimeout;
    }

    public boolean isPushEnabled() {
        return sLocalPrefs.isPushEnabled ||
                (mPreferences.getBoolean(Constants.PrefKeys.PUSH_ENABLED, false) && getPushSenderId() != null);
    }

    public String getPushSenderId() {
        String senderId = sLocalPrefs.getPushSenderId();
        if (!MPUtility.isEmpty(senderId))
            return senderId;
        else return mPreferences.getString(Constants.PrefKeys.PUSH_SENDER_ID, null);
    }

    public void setPushSenderId(String senderId){
        mPreferences.edit()
                .putString(Constants.PrefKeys.PUSH_SENDER_ID, senderId)
                .putBoolean(Constants.PrefKeys.PUSH_ENABLED, true)
                .apply();
    }

    public String getLicenseKey() {
        return sLocalPrefs.licenseKey;
    }

    public boolean isLicensingEnabled() {
        return sLocalPrefs.licenseKey != null && sLocalPrefs.isLicensingEnabled;
    }

    public void setPushSoundEnabled(boolean pushSoundEnabled) {
        mPreferences.edit()
            .putBoolean(Constants.PrefKeys.PUSH_ENABLE_SOUND, pushSoundEnabled)
            .apply();
    }

    public void setPushVibrationEnabled(boolean pushVibrationEnabled) {
        mPreferences.edit()
            .putBoolean(Constants.PrefKeys.PUSH_ENABLE_VIBRATION, pushVibrationEnabled)
            .apply();
    }

    public boolean isEnabled(){
        boolean optedOut = this.getOptedOut();
        return !optedOut || mSendOoEvents;
    }

    public void setOptOut(boolean optOut){
        mPreferences
                .edit().putBoolean(Constants.PrefKeys.OPTOUT, optOut).apply();
    }

    public boolean getOptedOut(){
        return mPreferences.getBoolean(Constants.PrefKeys.OPTOUT, false);
    }

    public boolean isAutoTrackingEnabled() {
        return sLocalPrefs.autoTrackingEnabled;
    }

    public boolean isPushSoundEnabled() {
        return mPreferences.getBoolean(Constants.PrefKeys.PUSH_ENABLE_SOUND, AppConfig.DEFAULT_ENABLE_PUSH_SOUND);
    }
    public boolean isPushVibrationEnabled() {
        return mPreferences.getBoolean(Constants.PrefKeys.PUSH_ENABLE_VIBRATION, AppConfig.DEFAULT_ENABLE_PUSH_VIBRATION);
    }

    public void setPushNotificationIcon(int pushNotificationIcon) {
        mPreferences.edit()
                .putInt(Constants.PrefKeys.PUSH_ICON, pushNotificationIcon)
                .apply();
    }

    public void setPushNotificationTitle(int pushNotificationTitle) {
        mPreferences.edit()
                .putInt(Constants.PrefKeys.PUSH_TITLE, pushNotificationTitle)
                .apply();
    }

    private static SharedPreferences getPreferences(Context context){
        return context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);
    }

    public static JSONArray getPushKeys(Context context){
        if (sPushKeys == null){
            String arrayString = getPreferences(context).getString(KEY_PUSH_MESSAGES, null);
            try {
                sPushKeys = new JSONArray(arrayString);
            } catch (Exception e) {
                sPushKeys = new JSONArray();
            }
        }
        return sPushKeys;
    }

    public static int getPushTitle(Context context) {
        return getPreferences(context)
                .getInt(Constants.PrefKeys.PUSH_TITLE, 0);
    }

    public static int getPushIcon(Context context) {
        return getPreferences(context)
                .getInt(Constants.PrefKeys.PUSH_ICON, 0);
    }

    public static int getBreadcrumbLimit() {
        if (mPreferences != null){
            return mPreferences.getInt(Constants.PrefKeys.BREADCRUMB_LIMIT, AppConfig.DEFAULT_BREADCRUMB_LIMIT);
        }
        return AppConfig.DEFAULT_BREADCRUMB_LIMIT;
    }

    public void setBreadcrumbLimit(int newLimit){
        mPreferences.edit()
                .putInt(Constants.PrefKeys.BREADCRUMB_LIMIT, newLimit)
                .apply();
    }

    private synchronized void setProviderPersistence(JSONObject persistence){
        mProviderPersistence = persistence;
    }

    public synchronized JSONObject getProviderPersistence() {
        return mProviderPersistence;
    }

    public void setMpid(long mpid) {
        mPreferences.edit().putLong(Constants.PrefKeys.Mpid, mpid).apply();
    }

    public long getMpid() {
        if (mPreferences.contains(Constants.PrefKeys.Mpid)){
            return mPreferences.getLong(Constants.PrefKeys.Mpid, 0);
        }else{
            long mpid = MPUtility.generateMpid();
            mPreferences.edit().putLong(Constants.PrefKeys.Mpid, mpid).apply();
            return mpid;
        }
    }

    public int getAudienceTimeout() {
        return sLocalPrefs.audienceTimeout;
    }

    public int getCurrentRampValue() {
        return mRampValue;
    }

    public JSONArray getTriggerMessageHashes() {
        return mTriggerMessageHashes;
    }

    public boolean shouldTrigger(MPMessage message) {
        JSONArray messageMatches = getTriggerMessageMatches();
        JSONArray triggerHashes = getTriggerMessageHashes();

        boolean shouldTrigger = message.getMessageType().equals(Constants.MessageType.PUSH_RECEIVED)
                || message.getMessageType().equals(Constants.MessageType.COMMERCE_EVENT)
                || message.getMessageType().equals(Constants.MessageType.APP_STATE_TRANSITION);

        if (!shouldTrigger && messageMatches != null && messageMatches.length() > 0){
            shouldTrigger = true;
            int i = 0;
            while (shouldTrigger && i < messageMatches.length()){
                try {
                    JSONObject messageMatch = messageMatches.getJSONObject(i);
                    Iterator<?> keys = messageMatch.keys();
                    while(shouldTrigger && keys.hasNext() ){
                        String key = (String)keys.next();
                        shouldTrigger = message.has(key);
                        if (shouldTrigger){
                            try {
                                shouldTrigger = messageMatch.getString(key).equalsIgnoreCase(message.getString(key));
                            }catch (JSONException stringex){
                                try {
                                    shouldTrigger = message.getBoolean(key) == messageMatch.getBoolean(key);
                                }catch (JSONException boolex){
                                    try{
                                        shouldTrigger = message.getDouble(key) == messageMatch.getDouble(key);
                                    }catch (JSONException doubleex){
                                        shouldTrigger = false;
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {

                }
                i++;
            }
        }
        if (!shouldTrigger && triggerHashes != null){
            for (int i = 0; i < triggerHashes.length(); i++){
                try {
                    if (triggerHashes.getInt(i) == message.getTypeNameHash()) {
                        shouldTrigger = true;
                        break;
                    }
                }catch (JSONException jse){

                }
            }
        }
        return shouldTrigger;
    }

    public int getUserBucket() {
        if (mUserBucket < 0){
            mUserBucket = (int)(Math.abs(getMpid() >> 8) % 100);
        }
        return mUserBucket;
    }

    public void setIntegrationAttributes(int kitId, Map<String, String> newAttributes) {
        try {
            JSONObject newJsonAttributes = null;
            if (newAttributes != null && !newAttributes.isEmpty()) {
                newJsonAttributes = new JSONObject();
                for (Map.Entry<String, String> entry : newAttributes.entrySet()) {
                    newJsonAttributes.put(entry.getKey(), entry.getValue());
                }
            }
            JSONObject currentJsonAttributes = getIntegrationAttributes();
            if (currentJsonAttributes == null) {
                currentJsonAttributes = new JSONObject();
            }
            currentJsonAttributes.put(Integer.toString(kitId), newJsonAttributes);
            if (currentJsonAttributes.length() > 0) {
                mPreferences.edit()
                        .putString(Constants.PrefKeys.INTEGRATION_ATTRIBUTES, currentJsonAttributes.toString())
                        .apply();
            } else {
                mPreferences.edit()
                        .remove(Constants.PrefKeys.INTEGRATION_ATTRIBUTES)
                        .apply();
            }
        }catch (JSONException jse) {

        }
    }

    public Map<String, String> getIntegrationAttributes(int kitId) {
        Map<String, String> integrationAttributes = new HashMap<String, String>();
        JSONObject jsonAttributes = getIntegrationAttributes();
        if (jsonAttributes != null) {
            JSONObject kitAttributes = jsonAttributes.optJSONObject(Integer.toString(kitId));
            if (kitAttributes != null) {
                try {
                    Iterator<String> keys = kitAttributes.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        if (kitAttributes.get(key) instanceof String) {
                            integrationAttributes.put(key, kitAttributes.getString(key));
                        }
                    }
                } catch (JSONException e) {

                }
            }
        }
        return integrationAttributes;
    }

    public JSONObject getIntegrationAttributes() {
        JSONObject jsonAttributes = null;
        String allAttributes = mPreferences.getString(Constants.PrefKeys.INTEGRATION_ATTRIBUTES, null);
        if (allAttributes != null) {
            try {
                jsonAttributes = new JSONObject(allAttributes);
            } catch (JSONException e) {

            }
        }
        return jsonAttributes;
    }

    public JSONArray getUserIdentityJson() {
        JSONArray userIdentities = null;
        String userIds = mPreferences.getString(Constants.PrefKeys.USER_IDENTITIES + getApiKey(), null);

        try {
            userIdentities = new JSONArray(userIds);
            boolean changeMade = fixUpUserIdentities(userIdentities);
            if (changeMade) {
                saveUserIdentityJson(userIdentities);
            }
        } catch (Exception e) {
            userIdentities = new JSONArray();
        }
        return userIdentities;
    }

    public void saveUserIdentityJson(JSONArray userIdentities) {
        mPreferences.edit().putString(Constants.PrefKeys.USER_IDENTITIES + getApiKey(), userIdentities.toString()).apply();
    }

    private static boolean fixUpUserIdentities(JSONArray identities) {
        boolean changeMade = false;
        try {
            for (int i = 0; i < identities.length(); i++) {
                JSONObject identity = identities.getJSONObject(i);
                if (!identity.has(Constants.MessageKey.IDENTITY_DATE_FIRST_SEEN)) {
                    identity.put(Constants.MessageKey.IDENTITY_DATE_FIRST_SEEN, 0);
                    changeMade = true;
                }
                if (!identity.has(Constants.MessageKey.IDENTITY_FIRST_SEEN)) {
                    identity.put(Constants.MessageKey.IDENTITY_FIRST_SEEN, true);
                    changeMade = true;
                }
            }

        } catch (JSONException jse) {

        }
        return changeMade;
    }


    public JSONObject getCookies() {
        if (mCurrentCookies == null) {
            String currentCookies = mPreferences.getString(Constants.PrefKeys.Cookies, null);
            if (MPUtility.isEmpty(currentCookies)) {
                mCurrentCookies = new JSONObject();
                mPreferences.edit().putString(Constants.PrefKeys.Cookies, mCurrentCookies.toString()).apply();
                return mCurrentCookies;
            } else {
                try {
                    mCurrentCookies = new JSONObject(currentCookies);
                } catch (JSONException e) {
                    mCurrentCookies = new JSONObject();
                }
            }
            Calendar nowCalendar = Calendar.getInstance();
            nowCalendar.set(Calendar.YEAR, 1990);
            Date oldDate = nowCalendar.getTime();
            SimpleDateFormat parser = new SimpleDateFormat("yyyy");
            Iterator<?> keys = mCurrentCookies.keys();
            ArrayList<String> keysToRemove = new ArrayList<String>();
            while (keys.hasNext()) {
                try {
                    String key = (String) keys.next();
                    if (mCurrentCookies.get(key) instanceof JSONObject) {
                        String expiration = ((JSONObject) mCurrentCookies.get(key)).getString("e");
                        try {
                            Date date = parser.parse(expiration);
                            if (date.before(oldDate)) {
                                keysToRemove.add(key);
                            }
                        } catch (ParseException dpe) {

                        }
                    }
                } catch (JSONException jse) {

                }
            }
            for (String key : keysToRemove) {
                mCurrentCookies.remove(key);
            }
            if (keysToRemove.size() > 0) {
                mPreferences.edit().putString(Constants.PrefKeys.Cookies, mCurrentCookies.toString()).apply();
            }
            return mCurrentCookies;
        } else {
            return mCurrentCookies;
        }
    }

    JSONArray markIdentitiesAsSeen(JSONArray uploadedIdentities) {
        try {

            JSONArray currentIdentities = getUserIdentityJson();
            if (currentIdentities.length() == 0) {
                return null;
            }
            uploadedIdentities = new JSONArray(uploadedIdentities.toString());
            Set<Integer> identityTypes = new HashSet<Integer>();
            for (int i = 0; i < uploadedIdentities.length(); i++) {
                if (uploadedIdentities.getJSONObject(i).optBoolean(Constants.MessageKey.IDENTITY_FIRST_SEEN)) {
                    identityTypes.add(uploadedIdentities.getJSONObject(i).getInt(Constants.MessageKey.IDENTITY_NAME));
                }
            }
            if (identityTypes.size() > 0) {
                for (int i = 0; i < currentIdentities.length(); i++) {
                    int identity = currentIdentities.getJSONObject(i).getInt(Constants.MessageKey.IDENTITY_NAME);
                    if (identityTypes.contains(identity)) {
                        currentIdentities.getJSONObject(i).put(Constants.MessageKey.IDENTITY_FIRST_SEEN, false);
                    }
                }
                return currentIdentities;
            }
        } catch (JSONException jse) {

        }
        return null;
    }
}