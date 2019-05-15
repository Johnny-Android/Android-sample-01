package com.windingo.vc;

import android.app.AlertDialog;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.BlurMaskFilter;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Space;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.WindowManager;

//import com.google.android.gms.ads.AdListener;
//import com.google.android.gms.ads.AdRequest;
//import com.google.android.gms.ads.AdSize;
//import com.google.android.gms.ads.AdView;
//import com.google.android.gms.ads.MobileAds;

import com.windingo.vc.util.IabBroadcastReceiver;
import com.windingo.vc.util.IabBroadcastReceiver.IabBroadcastListener;
import com.windingo.vc.util.IabHelper;
import com.windingo.vc.util.IabHelper.IabAsyncInProgressException;
import com.windingo.vc.util.IabResult;
import com.windingo.vc.util.Inventory;
import com.windingo.vc.util.Purchase;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormatSymbols;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;


public class MainActivity extends AppCompatActivity implements IabBroadcastListener, View.OnTouchListener
{
    // implementend by native-lib.cpp
    private InputAreaView mInputAreaView;

    private int topSpace;
    private int leftSpace;
    private float pinchBase;
    private static boolean inTouch = false;
    private static boolean inTutorial = false;
    private static final int EVENT_TOUCH_BEGIN    = 1;
    private static final int EVENT_TOUCH_MOVE     = 2;
    private static final int EVENT_TOUCH_END      = 3;
    private static final int EVENT_TOUCH_CANCEL   = 4;
    private static final int EVENT_TOUCH_PREZOOM  = 5;
    private static final int EVENT_TOUCH_ZOOM     = 6;

    private static native void initUiGate(InputAreaView inputAreaView, int left, int top, int width, int height, float dpi, boolean deviceIsTablet, String storagePlace, boolean inFullscreen);
    private static native void onTouch(int event, int x, int y, float scale);
    private static native void onOpenFile(String path);
    public static native int getUiSeparatorWidth();
    public static native int getUiBorderWidth();
    public static native boolean onBackRequested();
    private static native void freeUiGate();
    private static native void saveState();
    private static native int getUiTheme();
    private static native void setSeparators(char decimalSeparator, char groupSeparator);
    public static native boolean getVersionTag();
    public static native void setVersionTag(boolean isTrial);

    public static String mLocalTempPlace = "";
    public static String mLocalSharePlace = "";
    public static float mDensityDpi;
    public static float mDpi;

    private static final boolean emulate9x16 = false;
    private static final boolean emulatePhone = false;

    public boolean mInFullscreen = false;
    public boolean mTurnOffFullscreen = false;

    IabHelper mHelper; // in-app purchase helper
    static boolean CheckTrialOrFull = true; // query inventory only once (on app start) to avoid memory leaks
    IabBroadcastReceiver mBroadcastReceiver; // Provides purchase notification while this app is running

    @Override
    public void receivedBroadcast() {
        // Received a broadcast notification that the inventory of items has changed
        try {
            if (null != mHelper) {
                mHelper.queryInventoryAsync(mGotInventoryListener);
            }
        } catch (Exception e) {
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Display display = getWindowManager().getDefaultDisplay();
        Rect displayRect = new Rect();
        display.getRectSize(displayRect);

        DisplayMetrics dm = new DisplayMetrics();
        display.getMetrics(dm);
        mDensityDpi = dm.densityDpi;
        mDpi = Math.max(dm.xdpi, dm.ydpi);

        topSpace = displayRect.top;
        leftSpace = displayRect.left;

        int width = displayRect.width();
        int height = displayRect.height();
        boolean portrait = (height > width);

        float width_ = (float)width / dm.xdpi;
        float height_ = (float)height / dm.ydpi;
        boolean isTablet = !emulatePhone && (((!portrait ? height_ : width_) / 11.0f) > (0.75 / 2.54)); // landscape orientation: 5x11 buttons at the right side (portrait orientation: 8x5 buttons at the bottom side)
        //isTablet = false; // force phone UI

        // 2960x1440: samsung galaxy S8
        // 2560x1440: samsung galaxy note 4, samsung galaxy S6

        if (emulate9x16)
        {
            if (portrait)
            {
                width = height * 9 / 16;
            }
            else
            {
                height = width * 9 / 16;
            }
        }

        if (!portrait && (!isTablet || (height < (width * 720 / 1000)))) // !portrait && (!tablet || wide)
        {
            //if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) // < 16(0x10): June 2012: Android 4.1
            {
                mInFullscreen = true;
                getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }

            //else // Jellybean and up, new hotness
            //{
            //    View decorView = getWindow().getDecorView();
            //
            //    // Hide the status bar.
            //    int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
            //    decorView.setSystemUiVisibility(uiOptions);
            //
            //    // Remember that you should never show the action bar if the status bar is hidden, so hide that too if necessary.
            //    ActionBar actionBar = getActionBar();
            //    if (actionBar != null)
            //    {
            //        actionBar.hide();
            //    }
            //}
        }

        setContentView(R.layout.activity_main);
        mInputAreaView = InputAreaView.theInstance;

        if (emulate9x16)
        {
            android.view.ViewGroup.LayoutParams lp = mInputAreaView.getLayoutParams();
            lp.height = height;
            lp.width = width;
            mInputAreaView.setLayoutParams(lp);
        }

        mInputAreaView.init(this, width, height, isTablet);

        mFilesToolbar = (Toolbar)findViewById(R.id.files_toolbar);
        mFilesToolbarLight = (Toolbar)findViewById(R.id.files_toolbarLight);

        String localStoragePlace = getFilesDir().toString();
        setSeparators(DecimalFormatSymbols.getInstance().getDecimalSeparator(), DecimalFormatSymbols.getInstance().getGroupingSeparator());
        initUiGate(mInputAreaView, leftSpace, topSpace, width, height, (float)dm.densityDpi, isTablet, localStoragePlace, mInFullscreen);


        if (CheckTrialOrFull) { // trial | full version
            CheckTrialOrFull = false;
            performIabAction(new IabAction()
            {
                @Override
                public void onAction() throws IabAsyncInProgressException
                {
                    mHelper.queryInventoryAsync(mGotInventoryListener);
                }
            });
        }

        mInputAreaView.gotoFullscreenOnTutorial(); // if in tutorial

        if (mLocalTempPlace.isEmpty())
        {
            mLocalTempPlace = localStoragePlace + "/temp";
            new java.io.File(mLocalTempPlace).mkdir();
        }

        if (mLocalSharePlace.isEmpty())
        {
            mLocalSharePlace = localStoragePlace + "/exports";
            new java.io.File(mLocalSharePlace).mkdir();
        }

        applyTheme(getUiTheme());

        // if called to open/view a *.vcef file
        Intent theIntent = getIntent();
        if (null != theIntent)
        {
            Uri data = theIntent.getData();
            if (null != data)
            {
                String errorMessage = "";
                String fileToOpen = "";

                try
                {
                    String scheme = data.getScheme();
                    if (0 == scheme.compareToIgnoreCase("file"))
                    {
                        fileToOpen = data.getPath();
                        new FileInputStream(fileToOpen);
                    }
                    else if (0 == scheme.compareToIgnoreCase("content"))
                    {
                        InputStream input = getContentResolver().openInputStream(data);

                        fileToOpen = mLocalTempPlace + "/external_content.vcef";
                        OutputStream output = new java.io.FileOutputStream(fileToOpen);

                        try
                        {
                            try
                            {
                                int read;
                                byte[] buffer = new byte[4 * 1024];

                                while ((read = input.read(buffer)) != -1)
                                {
                                    output.write(buffer, 0, read);
                                }

                                output.flush();
                            }
                            finally
                            {
                                output.close();
                            }
                        }
                        finally
                        {
                            input.close();
                        }
                    }
                    else
                    {
                        errorMessage = "Cannot load data from:\n" + data;
                    }
                }
                catch(java.io.IOException e)
                {
                    fileToOpen = "";
                    errorMessage = "Cannot open file\n\n" + e.getLocalizedMessage();

                    if (errorMessage.contains("ermission denied")) {
                        errorMessage = "";
                        mInputAreaView.showSettingsPromptDialog("Cannot open file", "Permission denied.\n\nYou should allow storage in app permissions.");
                    }
                }

                try
                {
                    theIntent.setData(null);
                }
                catch (Exception e)
                {
                }

                if (!errorMessage.isEmpty())
                {
                    mInputAreaView.showErrorMessage(errorMessage.toCharArray(), errorMessage.length());
                }
                else if (!fileToOpen.isEmpty())
                {
                    onOpenFile(fileToOpen);
                }
            }
        }
    }

    // trial | full version ********************************************************************
    static final String SKU_FULL_VERSION_STUFF = "..";
    static final String SKU_FULL_VERSION_STUFF_PAYLOAD = "..";
    static final int IAB_RC_REQUEST = 10001;

    public interface IabAction {
        void onAction() throws IabAsyncInProgressException;
    }

    void performIabAction(final IabAction anAction)
    {
        if (null != mHelper) { // to avoid the IabAsyncInProgressException
            try { mHelper.disposeWhenFinished(); }
            catch (Exception e) {
                mInputAreaView.showErrorMessage("In-App Billing Call Failed\n\nPlease verify your account and Internet connection");
                return;
            }
        }

        mHelper = new IabHelper(this, "..");
        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {

                if (result.isSuccess() && (null != mHelper)) {

                    mBroadcastReceiver = new IabBroadcastReceiver(MainActivity.this);
                    android.content.IntentFilter broadcastFilter = new android.content.IntentFilter(IabBroadcastReceiver.ACTION);
                    registerReceiver(mBroadcastReceiver, broadcastFilter);

                    try { anAction.onAction(); } catch (Exception e) {
                        //mInputAreaView.showErrorMessage("Iab failed\n\n" + e.getLocalizedMessage());
                    }
                }
            }
        });
    }

    // Listener that's called when we finish querying the items and subscriptions we own
    IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
        public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
            if ((null != mHelper) && !result.isFailure()) {
                Purchase purchase = inventory.getPurchase(SKU_FULL_VERSION_STUFF);
                setVersionTag((null == purchase) || !purchase.getDeveloperPayload().equals(SKU_FULL_VERSION_STUFF_PAYLOAD));

                //if (null != purchase) {
                //    try { mHelper.consumeAsync(purchase, new IabHelper.OnConsumeFinishedListener() { public void onConsumeFinished(Purchase purchase, IabResult result) {}}); }
                //    catch (Exception e) {}
                //}
            }
        }
    };

    // Callback for when a purchase is finished
    IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
            if (null != mHelper) {
                if (result.isFailure()) {
                    if (mHelper.BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED == result.getResponse()) {
                        //mInputAreaView.showErrorMessage("Already owned\n\n" + purchase.getDeveloperPayload());
                        setVersionTag(false); // ..
                    }
                } else {
                    if ((purchase.getSku().equals(SKU_FULL_VERSION_STUFF)) && purchase.getDeveloperPayload().equals(SKU_FULL_VERSION_STUFF_PAYLOAD)) {
                        mInputAreaView.showMessage("Visual Calculator by Windingo", "          Thank you for purchasing Premium Pack!");
                        setVersionTag(false);
                    }
                }
            }
        }
    };

    public void onBuyFullVersion()
    {
        performIabAction(new IabAction() {
            @Override
            public void onAction() throws IabAsyncInProgressException {
                mHelper.launchPurchaseFlow(mInputAreaView.mMainActivity, SKU_FULL_VERSION_STUFF, IAB_RC_REQUEST, mPurchaseFinishedListener, SKU_FULL_VERSION_STUFF_PAYLOAD);
            }
        });
    }

    public static final int SHARE_ACTIVITY_REQUEST_CODE = 1212;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == SHARE_ACTIVITY_REQUEST_CODE)
        {
            mInputAreaView.showEditor();
        }
        else {
            // Pass on the activity result to the helper for handling
            if (!mHelper.handleActivityResult(requestCode, resultCode, data))
            {
                super.onActivityResult(requestCode, resultCode, data);
            } else {
                //mInputAreaView.showErrorMessage("In-App Billing Call Failed\n\nVerify your account!");
            }
        }
    }

    public Toolbar mFilesToolbar;
    public Toolbar mFilesToolbarLight;

    public void applyTheme(int themeIndex)
    {
        // change toolbar stuff color(s): https://stackoverflow.com/questions/33339043/how-to-change-color-of-toolbar-back-button-in-android

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) // 21 == November 2014
        {
            try
            {
                android.widget.EdgeEffect edgeEffectTop = new android.widget.EdgeEffect(this);
                edgeEffectTop.setColor(0 == themeIndex ? Color.BLACK : Color.LTGRAY);

                android.widget.EdgeEffect edgeEffectBottom = new android.widget.EdgeEffect(this);
                edgeEffectBottom.setColor(edgeEffectTop.getColor());

                java.lang.reflect.Field f1 = AbsListView.class.getDeclaredField("mEdgeGlowTop");
                f1.setAccessible(true);
                f1.set(mInputAreaView.mFileListView, edgeEffectTop);

                java.lang.reflect.Field f2 = AbsListView.class.getDeclaredField("mEdgeGlowBottom");
                f2.setAccessible(true);
                f2.set(mInputAreaView.mFileListView, edgeEffectBottom);

            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) // 23, M is for Marshmallow!
        {
            Window window_ = getWindow();
            View view_ = window_.getDecorView();
            int newUiVisibility = (int)view_.getSystemUiVisibility();

            if (0 == themeIndex) // dark theme
            {
                window_.setStatusBarColor(getResources().getColor(R.color.colorPrimaryDark, null));
                newUiVisibility &= ~(int)(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR); // Light Text to show up on your dark status bar
            }
            else // light theme
            {
                window_.setStatusBarColor(getResources().getColor(R.color.colorPrimaryDarkText, null));
                newUiVisibility |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; // Dark Text to show up on your light status bar
            }

            view_.setSystemUiVisibility(newUiVisibility);
        }

        if (0 == themeIndex) // dark theme
        {
            mFilesToolbar.setVisibility(View.VISIBLE);
            mFilesToolbarLight.setVisibility(View.GONE);
            mInputAreaView.mSettingsToolbar.setVisibility(View.VISIBLE);
            mInputAreaView.mSettingsToolbarLight.setVisibility(View.GONE);

            setSupportActionBar(mFilesToolbar);
            mFilesToolbar.setNavigationOnClickListener(mInputAreaView.mNavigationOnClickListener);
        }
        else // light theme
        {
            mFilesToolbar.setVisibility(View.GONE);
            mFilesToolbarLight.setVisibility(View.VISIBLE);
            mInputAreaView.mSettingsToolbar.setVisibility(View.GONE);
            mInputAreaView.mSettingsToolbarLight.setVisibility(View.VISIBLE);

            setSupportActionBar(mFilesToolbarLight);
            mFilesToolbarLight.setNavigationOnClickListener(mInputAreaView.mNavigationOnClickListener);
        }

        supportInvalidateOptionsMenu();
    }

    public Menu filesMenu = null;
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_files, menu);
        filesMenu = menu;

        if (inTutorial)
        {
            //..
        }
        else if (inTouch)
        {
            onTouch(EVENT_TOUCH_CANCEL, 0, 0, 0.0f);
            inTouch = false;
        }

        showKeyboard(false);
        mInputAreaView.reshowUi();
        mInputAreaView.reshowAlertDialog();
        mInputAreaView.onClipboardChanged();

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //no inspection SimplifiableIfStatement
        if (id == R.id.action_select_files)
        {
            mInputAreaView.toggleFileSelection(true);
            return true;
        }

        else if (id == R.id.action_select_all)
        {
            mInputAreaView.selectAll();
            return true;
        }

        else if (id == R.id.action_unselect_all)
        {
            mInputAreaView.unselectAll();
            return true;
        }

        else if (id == R.id.action_multiple_delete)
        {
            mInputAreaView.deleteSelectedFiles();
            return true;
        }

        else if (id == R.id.action_newfile)
        {
            if (mInputAreaView.mFileList.isEmpty() || !getVersionTag()) { // true == trial version
                mInputAreaView.showNewFileInteface(true);
            }
            else {
                mInputAreaView.showErrorMessage("Cannot create file\n\nThe free version can create only one file.\n\nWhen you get the Premium Pack you'll forget about the restrictions.\n");
            }

            return true;
        }

        else if (id == R.id.action_cancel_selection_mode)
        {
            mInputAreaView.toggleFileSelection(false);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void showKeyboard(boolean visible)
    {
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);

        if (visible)
        {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
            mInputAreaView.mEditTextInputEater.requestFocus();

            imm.showSoftInput(mInputAreaView.mEditTextInputEater, InputMethodManager.SHOW_FORCED);
        }
        else
        {
            mInputAreaView.mEditTextInputEater.clearFocus();
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

            imm.hideSoftInputFromWindow(mInputAreaView.getWindowToken(), 0);
        }
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        mInputAreaView.stopCursorTimer();
        saveState();
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        mInputAreaView.startCursorTimer();
    }

    @Override
    public void onBackPressed()
    {
        if (!mInputAreaView.onBackProcessed())
        {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent event)
    {
        int actionMask = event.getActionMasked();
        int pointerIndex = event.getActionIndex();
        int pointerCount = event.getPointerCount();

        if (inTutorial)
        {
            //..
        }
        else
        {
            int x = (int)event.getX(pointerIndex);
            int y = (int)event.getY(pointerIndex);

            switch (actionMask)
            {
                case MotionEvent.ACTION_DOWN: // first touch
                case MotionEvent.ACTION_POINTER_DOWN: // following touches
                    inTouch = false;
                    if (1 == pointerCount)
                    {
                        inTouch = true;
                        onTouch(EVENT_TOUCH_BEGIN, x - leftSpace, y - topSpace, 0.0f);
                    }
                    else if (2 == pointerCount)
                    {
                        onTouch(EVENT_TOUCH_PREZOOM, x - leftSpace, y - topSpace, 0.0f);
                    }
                    pinchBase = 0;
                    break;


                case MotionEvent.ACTION_UP: // last up
                case MotionEvent.ACTION_POINTER_UP: // not last up
                    if (inTouch)
                    {
                        onTouch(EVENT_TOUCH_END, x - leftSpace, y - topSpace, 0.0f);
                    }
                    else if (1 == pointerCount)
                    {
                        onTouch(EVENT_TOUCH_CANCEL, 0, 0, 0.0f);
                    }
                    inTouch = false;
                    pinchBase = 0;
                    break;


                case MotionEvent.ACTION_MOVE: // ..
                    if (inTouch && (1 == pointerCount))
                    {
                        onTouch(EVENT_TOUCH_MOVE, x - leftSpace, y - topSpace, 0.0f);
                    }
                    else if (2 == pointerCount)
                    {
                        float x1 = (int)event.getX(0);
                        float y1 = (int)event.getY(0);
                        float x2 = (int)event.getX(1);
                        float y2 = (int)event.getY(1);

                        float dx = x1 - x2;
                        float dy = y1 - y2;
                        float dxy = (float)Math.sqrt((double)(dx * dx + dy * dy));

                        if (0.0f == pinchBase)
                        {
                            pinchBase = dxy;
                        }
                        else
                        {
                            onTouch(EVENT_TOUCH_ZOOM, (int)(x1 + x2) / 2 - leftSpace, (int)(y1 + y2) / 2 - topSpace, dxy / pinchBase);
                        }
                    }
                    break;
            }

        }

        return true;
    }

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }
}

// Note: suppressing lint warning for ViewConstructor since it is
//       manually set from the activity and not used in any layout.
@SuppressLint("ViewConstructor")
class InputAreaView extends View implements AdapterView.OnItemSelectedListener
{
    public MainActivity mMainActivity;

    public static android.content.ClipboardManager mClipboard = null;

    public EditText mEditTextInputEater;

    public java.text.DateFormat mDateFormat;

    public Toolbar mSettingsToolbar;
    public Toolbar mSettingsToolbarLight;
    public View.OnClickListener mNavigationOnClickListener;

    public LinearLayout mSettingsView;
    public TextView mSettingsPrecisionTitleTextView;
    public LinearLayout mSettingsPrecisionExprLayout;
    public TextView mSettingsPrecisionExprTitleTextView;
    public Spinner mSettingsPrecisionExprSpinner;
    public LinearLayout mSettingsPrecisionTableLayout;
    public TextView mSettingsPrecisionTableTitleTextView;
    public Spinner mSettingsPrecisionTableSpinner;
    public LinearLayout mSettingsNotationLayout;
    public TextView mSettingsNotationTitleTextView;
    public Spinner mSettingsNotationSpinner;
    public LinearLayout mSettingsConvertLayout;
    public TextView mSettingsConvertTitleTextView;
    public Spinner mSettingsConvertSpinner;
    public LinearLayout mSettingsFunctionLayout;
    public TextView mSettingsFunctionTitleTextView;
    public Spinner mSettingsFunctionSpinner;
    public LinearLayout mSettingsFontLayout;
    public TextView mSettingsFontTitleTextView;
    public Spinner mSettingsFontSpinner;
    public LinearLayout mSettingsThemeLayout;
    public TextView mSettingsThemeTitleTextView;
    public Spinner mSettingsThemeSpinner;
    public Switch mSwitchColoredButtons;
    public Switch mSwitchSoundEffects;
    public Switch mSwitchFractions;
    public LinearLayout mSettingsResetLayout;
    public Button mButtonResetCustomFormat;
    public int mSettingsTop;
    public int mSettingsTop2;
    public int mSettingsLeft;
    public int mSettingsRight;
    public int mSettingsBottom;

    public LinearLayout mFilesView;
    public ListView mFileListView;
    public int mFileListTop;
    public int mFileListLeft;
    public int mFileListRight;
    public int mFileListBottom;

    private static ArrayList<Paint> mFonts = null;
    private static BlurMaskFilter mBlurFilter = null;

    private static boolean mScreenKeyboardIsVisible = false;
    private boolean mScreenKeyboardStateIsValid;
    private int mInputAreaHeight;
    private int mKeyboardHeight;

    private char[] mChar;
    private Bitmap mBitmap;
    private Canvas mCanvas;
    private float[] mPoints;
    private Paint mCommonPaint;
    private Rect mRectForInvalidate;
    private MediaPlayer mBeepPlayer;
    private MediaPlayer mTockPlayer;

    private int[] mStringAttributes; // begin & length
    private static final int STRING_ATTRIBUTES_SIZE = 10;

    // implementend by native-lib.cpp
    private static native boolean renderInputArea(Canvas canvas, int left, int top, int width, int height, int clipRectLeft, int clipRectTop, int clipRectWidth, int clipRectHeight, boolean inMemory);
    private static native void prepareVceBannerBackground(int brush, int width_i, int height_i, boolean drawRoundTopRight);
    private static native void captureTutorialPage(int pageWidth, int pageHeight, int pageId);
    private static native void animateTutorial();
    private static native boolean hideButtonPanel();

    private static native void updateKeyboardHeight(int keyboardHeight);
    private static native void onInput(String oldText, String newText);
    private static native void onPaste(String text, boolean fromUiButton);
    private static native void onCanPasteChanged(boolean backspace);
    private static native void onBuyFullVersionClose(int option);
    private static native void onDelete(boolean backspace);
    private static native void onCharInput(char input);
    private static native void onCursorTimerTick();

    private static final int CURSOR_MOVING_UP = 0;
    private static final int CURSOR_MOVING_DOWN = 1;
    private static final int CURSOR_MOVING_LEFT = 2;
    private static final int CURSOR_MOVING_RIGHT = 3;
    private static final int CURSOR_MOVING_HOME = 4;
    private static final int CURSOR_MOVING_END = 5;
    private static final int CURSOR_MOVING_GLOBAL_HOME = 6;
    private static final int CURSOR_MOVING_GLOBAL_END = 7;

    private static native void onCursor(int moving, boolean select);

    private static final int SHORTCUT_MAIN_MENU = 0;
    private static final int SHORTCUT_OPEN = 1;
    private static final int SHORTCUT_SAVE = 2;
    private static final int SHORTCUT_SAVE_AS = 3;
    private static final int SHORTCUT_SHARE = 4;
    private static final int SHORTCUT_SETTINGS = 5;
    private static final int SHORTCUT_TUTORIAL = 6;
    private static final int SHORTCUT_FUNCTIONS = 7;
    private static final int SHORTCUT_BRACKETS = 8;
    private static final int SHORTCUT_CUSTOM_FORMAT = 9;
    private static final int SHORTCUT_INSERT_SUPERSCRIPT = 10;
    private static final int SHORTCUT_INSERT_SUBSCRIPT = 11;
    private static final int SHORTCUT_INSERT_FRACTION = 12;
    private static final int SHORTCUT_INSERT_ROOT = 13;
    private static final int SHORTCUT_INSERT_SUM = 14;
    private static final int SHORTCUT_CLEAR = 15;
    private static final int SHORTCUT_TOGGLE_COMMENT = 16;
    private static final int SHORTCUT_INSERT_PREVIOUS_RESULT = 17;
    private static final int SHORTCUT_CUT = 18;
    private static final int SHORTCUT_COPY = 19;
    private static final int SHORTCUT_PASTE = 20;
    private static final int SHORTCUT_UNDO = 21;
    private static final int SHORTCUT_REDO = 22;
    private static final int SHORTCUT_SELECT_ALL = 23;
    private static final int SHORTCUT_CANCEL = 24;

    private static final int CUSTOM_FORMAT_NOTATION_DEFAULT = 0;
    private static final int CUSTOM_FORMAT_NOTATION_DECIMAL = 1;
    private static final int CUSTOM_FORMAT_NOTATION_FRACTION = 2;

    private static native void onShortcut(int shortcut);
    private static native void applyCustomFormat(int contextIndex, int precisionIndex, int conversionIndex, boolean isCustomized, int notation);
    private static native void applyGlobalSettings(int precisionIndex, int tablePrecisionIndex, int fontIndex, int themeIndex, boolean fractionsOn, boolean soundOn, boolean colouredButtonsOn);

    // timer stuff, see: https://android-developers.googleblog.com/2007/11/stitch-in-time.html
    private static final long CURSOR_TIMER_INTERVAL = 150; // 100?
    Runnable cursorTimer = new Runnable()
    {
        @Override
        public void run() {
            onCursorTimerTick();
            postDelayed(cursorTimer, CURSOR_TIMER_INTERVAL);

            if (!mScreenKeyboardStateIsValid && mScreenKeyboardIsVisible)
            {
                showScreenKeyboard(true);
            }
            mScreenKeyboardStateIsValid = true;
        }
    };

    public void stopCursorTimer()
    {
        removeCallbacks(cursorTimer);
    }

    public void startCursorTimer()
    {
        postDelayed(cursorTimer, CURSOR_TIMER_INTERVAL);
    }

    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        // An item was selected. You can retrieve the selected item using
        // parent.getItemAtPosition(pos)

            applyOptions();
    }

    public void onNothingSelected(AdapterView<?> parent) {
        // Another interface callback
    }

    public static InputAreaView theInstance = null;

    //public InputAreaView(Context context, Rect rect)
    public InputAreaView(Context context, android.util.AttributeSet attributeSet)
    {
        super(context);

        theInstance = this;
        mMainActivity = null;

        mKeyboardHeight = 0;
        mInputAreaHeight = 0;
        mScreenKeyboardStateIsValid = false;

        if (!isInEditMode())
        {
            mCanvas = null;
            mChar = new char[2];
            mPoints = new float[16];

            mCommonPaint = new Paint();
            mCommonPaint.setAntiAlias(true);
            mCommonPaint.setStrokeMiter(1.0f);
            mCommonPaint.setStyle(Paint.Style.FILL);
            mCommonPaint.setStrokeCap(Paint.Cap.ROUND);
            mCommonPaint.setStrokeJoin(Paint.Join.ROUND);

            mStringAttributes = new int[STRING_ATTRIBUTES_SIZE * 2];

            if (null == mFonts) {
                mFonts = new ArrayList<Paint>();
                mBlurFilter = new BlurMaskFilter(60.0f, BlurMaskFilter.Blur.NORMAL);
            }

            mRectForInvalidate = new Rect();
            startCursorTimer();
        }
    }

    private final String INPUT_FILLER1 = "0123\r\n4 ";
    private final String INPUT_FILLER2 = "\t5\r\n6789";
    private final String INPUT_FILLER = INPUT_FILLER1 + INPUT_FILLER2;
    private final int INPUT_FILLER_CURSOR = 8;
    private final int INPUT_FILLER1_LENGTH = 8;

    private boolean mInInputContextUpdating;
    private boolean mForcedUpdateInputContext;
    private boolean mInputContextChanged;
    private String mInputContextText;
    private int mInputContextCursor;
    private int mInputContextSelectionEdge;
    private String mInputEaterText;
    private int mInputEaterCursor;
    private int mInputEaterSelectionEdge;

    private void updateInputEaterData() {
        mInputEaterText = mEditTextInputEater.getText().toString();
        mInputEaterCursor = mEditTextInputEater.getSelectionStart();
        mInputEaterSelectionEdge = mEditTextInputEater.getSelectionEnd();
    }

    private void resetInputEaterData() {
        mInInputContextUpdating = true;

        mEditTextInputEater.setText(INPUT_FILLER);
        mEditTextInputEater.setSelection(INPUT_FILLER_CURSOR, INPUT_FILLER_CURSOR);

        updateInputEaterData();
        mInInputContextUpdating = false;
    }

    public void updateInputContext(char[] text, int count, int cursor, int selectionEdge) {
        if (mEditTextInputEater.isEnabled()) {
            mInputContextChanged = true;
            mInputContextText = new String(text, 0, count);
            mInputContextCursor = cursor;
            mInputContextSelectionEdge = selectionEdge;

            updateInputContext();
        }
    }

    Runnable inputContextUpdater = new Runnable() {
        @Override
        public void run() {
            updateInputContext();
        }
    };

    private void updateInputContext() {
        if (mInputContextChanged && mEditTextInputEater.isEnabled()) {
            if (mInInputContextUpdating) {
                postDelayed(inputContextUpdater, 100);
            } else {
                if (!mScreenKeyboardIsVisible) {
                    resetInputEaterData();
                } else {
                    mInInputContextUpdating = true;
                    mInputContextChanged = false;

                    int selectionStart = 0;
                    int selectionEnd = 0;

                    if (mInputContextCursor > mInputContextSelectionEdge) // selection before cursor
                    {
                        selectionStart = INPUT_FILLER1_LENGTH + mInputContextSelectionEdge;
                        selectionEnd = INPUT_FILLER1_LENGTH + mInputContextCursor;
                    } else // selection after cursor
                    {
                        selectionStart = INPUT_FILLER1_LENGTH + mInputContextCursor;
                        selectionEnd = INPUT_FILLER1_LENGTH + mInputContextSelectionEdge;
                    }

                    boolean forceSelect = false;
                    String newText = INPUT_FILLER1 + mInputContextText + INPUT_FILLER2;
                    if (mForcedUpdateInputContext || !newText.equals(mEditTextInputEater.getText().toString())) {
                        forceSelect = true;
                        mForcedUpdateInputContext = false;
                        mEditTextInputEater.setText(newText);
                    }

                    if (forceSelect || (selectionStart != mEditTextInputEater.getSelectionStart()) || (selectionEnd != mEditTextInputEater.getSelectionEnd())) {
                        mEditTextInputEater.setSelection(selectionStart, selectionEnd);
                    }

                    updateInputEaterData();
                    mInInputContextUpdating = false;
                }
            }
        }
    }

    private Spinner initSpinner(MainActivity mainActivity, int spinnerId, int itemsId)
    {
        Spinner spinner = (Spinner) mainActivity.findViewById(spinnerId);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(mainActivity, itemsId, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); // Specify the layout to use when the list of choices appears
        spinner.setAdapter(adapter); // Apply the adapter to the spinner
        return spinner;
    }

    public void onClipboardChanged()
    {
        onCanPasteChanged(mClipboard.hasPrimaryClip() && mClipboard.getPrimaryClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN));
    }

    public boolean onBackProcessed()
    {
        boolean processed = true;

        if (inSelectFiles)
        {
            toggleFileSelection(false);
        }
        else if (mMainActivity.onBackRequested())
        {
            onClosePopup();
        }
        else
        {
            processed = hideButtonPanel();
        }

        return processed;
    }

    private void reduceTopMargin(LinearLayout ll)
    {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams)ll.getLayoutParams();
        lp.topMargin = lp.topMargin * 75 / 100;
        ll.setLayoutParams(lp);
    }

    private void reduceTopMargin(Switch ll, int heightFactor100)
    {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams)ll.getLayoutParams();
        lp.topMargin = lp.topMargin * heightFactor100 / 100;
        ll.setLayoutParams(lp);
    }

    private boolean mIsTablet;
    public void init(MainActivity mainActivity, int width, int height, boolean isTablet)
    {
        mIsTablet = isTablet;
        mMainActivity = mainActivity;
        setOnTouchListener(mMainActivity);

        mEditTextInputEater = (EditText) mMainActivity.findViewById(R.id.editTextInputEater);

        mDateFormat = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.FULL, java.text.DateFormat.SHORT);
        if (Locale.US == Locale.getDefault())
        {
            try {
                //new android.widget.TextClock().is24HourModeEnabled();
                if (android.text.format.DateFormat.is24HourFormat(mMainActivity))
                {
                    mDateFormat = new java.text.SimpleDateFormat("EEEE, MMMM d, yyyy 'at' HH:mm");
                }
                else
                {
                    mDateFormat = new java.text.SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm a");
                }
            }
            catch (java.lang.Exception e)
            {
                mDateFormat = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.FULL, java.text.DateFormat.SHORT);
            }
        }

        boolean portrait = width < height;

        mSettingsView = (LinearLayout) mMainActivity.findViewById(R.id.settingsView);
        mSettingsView.setVisibility(INVISIBLE);

        mSettingsToolbar = (Toolbar) mMainActivity.findViewById(R.id.options_toolbar);
        mSettingsToolbarLight = (Toolbar) mMainActivity.findViewById(R.id.options_toolbarLight);
        mNavigationOnClickListener = new View.OnClickListener() { @Override public void onClick(View v) { onBackProcessed(); } };
        mSettingsToolbarLight.setNavigationOnClickListener(mNavigationOnClickListener);
        mSettingsToolbar.setNavigationOnClickListener(mNavigationOnClickListener);

        mSettingsPrecisionExprLayout  = (LinearLayout) mMainActivity.findViewById(R.id.settings_precision_expr_layout);
        mSettingsPrecisionTableLayout = (LinearLayout) mMainActivity.findViewById(R.id.settings_precision_table_layout);
        mSettingsNotationLayout       = (LinearLayout) mMainActivity.findViewById(R.id.settings_notation_layout);
        mSettingsConvertLayout        = (LinearLayout) mMainActivity.findViewById(R.id.settings_convert_layout);
        mSettingsFunctionLayout       = (LinearLayout) mMainActivity.findViewById(R.id.settings_function_layout);
        mSettingsFontLayout           = (LinearLayout) mMainActivity.findViewById(R.id.settings_font_layout);
        mSettingsThemeLayout          = (LinearLayout) mMainActivity.findViewById(R.id.settings_theme_layout);
        mSettingsResetLayout          = (LinearLayout) mMainActivity.findViewById(R.id.settings_reset_layout);

        mSettingsPrecisionTitleTextView      = (TextView) mMainActivity.findViewById(R.id.options_precision_title);
        mSettingsPrecisionExprTitleTextView  = (TextView) mMainActivity.findViewById(R.id.options_precision_expr_title);
        mSettingsPrecisionTableTitleTextView = (TextView) mMainActivity.findViewById(R.id.options_precision_table_title);
        mSettingsNotationTitleTextView       = (TextView) mMainActivity.findViewById(R.id.options_notation_title);
        mSettingsConvertTitleTextView        = (TextView) mMainActivity.findViewById(R.id.options_convert_title);
        mSettingsFunctionTitleTextView       = (TextView) mMainActivity.findViewById(R.id.options_function_title);
        mSettingsFontTitleTextView           = (TextView) mMainActivity.findViewById(R.id.options_font_title);
        mSettingsThemeTitleTextView          = (TextView) mMainActivity.findViewById(R.id.options_theme_title);

        mSettingsPrecisionExprSpinner  = initSpinner(mMainActivity, R.id.spinnerPrecisionExpr, R.array.result_precision);
        mSettingsPrecisionTableSpinner = initSpinner(mMainActivity, R.id.spinnerPrecisionTable, R.array.table_precision);
        mSettingsFunctionSpinner       = initSpinner(mMainActivity, R.id.spinnerFunctionOptions, R.array.function_options);
        mSettingsNotationSpinner       = initSpinner(mMainActivity, R.id.spinnerNotation, R.array.notation_options);
        mSettingsConvertSpinner        = initSpinner(mMainActivity, R.id.spinnerConvertTo, R.array.custom_format);
        mSettingsFontSpinner           = initSpinner(mMainActivity, R.id.spinnerFont, R.array.calculation_font);
        mSettingsThemeSpinner          = initSpinner(mMainActivity, R.id.spinnerTheme, R.array.color_theme);

        mSwitchFractions = (Switch) mMainActivity.findViewById(R.id.switchFractions);
        mSwitchSoundEffects = (Switch) mMainActivity.findViewById(R.id.switchSoundEffects);
        mSwitchColoredButtons = (Switch) mMainActivity.findViewById(R.id.switchColouredButtons);
        mSwitchFractions.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { applyOptions(); } });
        mSwitchSoundEffects.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { applyOptions(); } });
        mSwitchColoredButtons.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { applyOptions(); } });

        mButtonResetCustomFormat = (Button) mMainActivity.findViewById(R.id.resetCustomFormat);
        mButtonResetCustomFormat.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { applyCustomFormat(0, 0, 0, false, CUSTOM_FORMAT_NOTATION_DEFAULT); } });

        Button toPlayMarket = (Button) mMainActivity.findViewById(R.id.gotoPlayMarket);
        toPlayMarket.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                Intent browse = new Intent(Intent.ACTION_VIEW , Uri.parse("https://play.google.com/store/apps/details?id=com.windingo.visualcalculationeditor"));
                mMainActivity.startActivity( browse );
            }
        });


        if (!isTablet)
        {
            reduceTopMargin(mSettingsPrecisionExprLayout );
            reduceTopMargin(mSettingsPrecisionTableLayout);
            reduceTopMargin(mSettingsConvertLayout       );
            reduceTopMargin(mSettingsNotationLayout      );
            reduceTopMargin(mSettingsFunctionLayout      );
            reduceTopMargin(mSettingsFontLayout          );
            reduceTopMargin(mSettingsThemeLayout         );
            reduceTopMargin(mSettingsResetLayout         );

            reduceTopMargin(mSwitchFractions, 100);
            reduceTopMargin(mSwitchSoundEffects, 50);
            reduceTopMargin(mSwitchColoredButtons, 50);
        }

        mMainActivity.findViewById(R.id.settingsBottomSpace).setMinimumHeight(height);

        // settings & file panels sizes

        mSettingsTop = 0;
        mSettingsTop2 = 0;
        mSettingsRight = width;
        mSettingsBottom = height;

        int settingWidth = width;
        int scaledBaseWidth = (int)(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 300, getResources().getDisplayMetrics()) * 0.60f);

        int xStuffExtraSpace = mMainActivity.getUiBorderWidth();
        if (0 == xStuffExtraSpace)
        {
            xStuffExtraSpace = (((portrait ? width : height) * 100) / (isTablet ? 768 : 320)) > 250 ? 2 : 1;
        }
        int inputAreaUiSeparatorWidth = mMainActivity.getUiSeparatorWidth() + xStuffExtraSpace;
        LinearLayout settingsViewRect = (LinearLayout) mMainActivity.findViewById(R.id.settingsViewRect);
        LinearLayout.LayoutParams lllp = (LinearLayout.LayoutParams)settingsViewRect.getLayoutParams();

        if (isTablet || !portrait)
        {
            settingWidth = scaledBaseWidth * 2;

            lllp.topMargin = xStuffExtraSpace;
            lllp.leftMargin = inputAreaUiSeparatorWidth;
        }
        else
        {
            lllp.topMargin = 0;
            lllp.leftMargin = 0;

            int settingHeight = scaledBaseWidth * 220 / 100; // * 0.127 * 10
            mSettingsTop = mSettingsBottom > settingHeight ? mSettingsBottom - settingHeight : 0;
            mSettingsTop2 = mSettingsTop + (mSettingsBottom - mSettingsTop) / 4;
        }

        settingsViewRect.setLayoutParams(lllp);
        mSettingsLeft = (mSettingsRight * 80 / 100) > settingWidth ? mSettingsRight - settingWidth : 0;

        int fileListWidth = scaledBaseWidth * (isTablet ? portrait ? 275 : 375 : portrait ? 200 : 275) / 100;
        mFileListTop = 0;
        mFileListLeft = width > fileListWidth ? width - fileListWidth : 0;
        mFileListRight = width;
        mFileListBottom = height;

        mFilesView = (LinearLayout) mMainActivity.findViewById(R.id.filesView);
        mFileListView = (ListView) mMainActivity.findViewById(R.id.fileListView);
        mFilesView.setVisibility(INVISIBLE);

        mFileListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        mFileListView.setMultiChoiceModeListener(new android.widget.AbsListView.MultiChoiceModeListener() {

            @Override
            public boolean onPrepareActionMode(android.view.ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public void onDestroyActionMode(android.view.ActionMode mode) {
            }

            @Override
            public boolean onCreateActionMode(android.view.ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(final android.view.ActionMode mode, MenuItem item) {
                return false;
            }

            @Override
            public void onItemCheckedStateChanged(android.view.ActionMode mode, int position, long id, boolean checked) {
            }
        });

        Space filesViewleftSpace = (Space) mMainActivity.findViewById(R.id.fileListLeftSpace);
        ViewGroup.LayoutParams lp = filesViewleftSpace.getLayoutParams();
        lp.width = mFileListLeft;
        filesViewleftSpace.setLayoutParams(lp);

        if (null == mClipboard) // if it isn't static then memory leak (on each mClipboard.addPrimaryClipChangedListener())
        {
            mClipboard = (android.content.ClipboardManager) mMainActivity.getSystemService(Context.CLIPBOARD_SERVICE);
            mClipboard.addPrimaryClipChangedListener(new ClipboardManager.OnPrimaryClipChangedListener()
            {
                @Override
                public void onPrimaryClipChanged()
                {
                    onClipboardChanged();
                }
            });
        }

        LinearLayout filesViewRect = (LinearLayout) mMainActivity.findViewById(R.id.filesViewRect);
        lllp = (LinearLayout.LayoutParams)filesViewRect.getLayoutParams();
        lllp.leftMargin = mFileListLeft > 0 ? inputAreaUiSeparatorWidth : 0;
        lllp.topMargin = xStuffExtraSpace;
        filesViewRect.setLayoutParams(lllp);

        resetTutorialStuff();

        resetInputEaterData();
        mInputContextChanged = false;
        mForcedUpdateInputContext = false;
        mEditTextInputEater.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                // TODO Auto-generated method stub
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // TODO Auto-generated method stub
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!mInInputContextUpdating) {
                    mInInputContextUpdating = true;

                    final int newCursorPos = mEditTextInputEater.getSelectionStart();
                    final boolean notEmptySelection = newCursorPos != mEditTextInputEater.getSelectionEnd();

                    StringBuilder oldText = new StringBuilder(mInputEaterText);
                    int oldTextLength = oldText.length();

                    StringBuilder newText = new StringBuilder(mEditTextInputEater.getText().toString());
                    int newTextLength = newText.length();

                    boolean noDifference = oldTextLength == newTextLength;
                    for (int i = 0; noDifference && (i < oldTextLength); ++i) {
                        noDifference = oldText.charAt(i) == newText.charAt(i);
                    }

                    if (!noDifference) {
                        int diffTextBegin = 0;
                        int diffTextBeginMax = Math.min(oldTextLength, Math.min(newTextLength, Math.min(newCursorPos, Math.min(mInputEaterCursor, mInputEaterSelectionEdge))));

                        while ((diffTextBegin < diffTextBeginMax) && (oldText.charAt(diffTextBegin) == newText.charAt(diffTextBegin))) {
                            ++diffTextBegin;
                        }

                        final int oldCursorPos = mInputEaterSelectionEdge;
                        while ((diffTextBegin < oldTextLength) && (diffTextBegin < newTextLength) && (newTextLength > newCursorPos) && (oldTextLength > oldCursorPos)) {
                            if (oldText.charAt(oldTextLength - 1) != newText.charAt(newTextLength - 1)) {
                                break;
                            }

                            --oldTextLength;
                            --newTextLength;
                        }

                        if (diffTextBegin > 0) {
                            oldText = oldText.delete(0, diffTextBegin);
                            newText = newText.delete(0, diffTextBegin);
                            oldTextLength -= diffTextBegin;
                            newTextLength -= diffTextBegin;
                        }

                        if (oldTextLength < oldText.length()) {
                            oldText.setLength(oldTextLength);
                        }

                        if (newTextLength < newText.length()) {
                            newText.setLength(newTextLength);
                        }

                        if ((0 == newText.length()) && (notEmptySelection || (1 == oldTextLength))) // delete
                        {
                            onDelete(mInputEaterCursor > diffTextBegin);
                        } else // input/replace
                        {
                            for (int i = 0; i < oldText.length(); ++i) {
                                if ('\r' == oldText.charAt(i)) {
                                    oldText.delete(i, i + 1);
                                    --i;
                                }
                            }

                            for (int i = 0; i < newText.length(); ++i) {
                                if ('\r' == newText.charAt(i)) {
                                    newText.delete(i, i + 1);
                                    --i;
                                }
                            }

                            if ((0 == oldText.length()) && (1 == newText.length())) {
                                onCharInput(newText.charAt(0));
                            } else {
                                onInput(oldText.toString(), newText.toString());
                            }
                        }

                        updateInputEaterData();
                        mForcedUpdateInputContext = true;
                    }

                    mInInputContextUpdating = false;
                }
            }

        });

        mEditTextInputEater.setOnKeyListener(new EditText.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                boolean res = false;

                if (!mInInputContextUpdating) {
                    mInInputContextUpdating = true;

                    final int KM_ALT = 0x01;
                    final int KM_CTRL = 0x02;
                    final int KM_SHIFT = 0x04;
                    boolean ctrl = event.isCtrlPressed();
                    int modifiers = (event.isAltPressed() ? KM_ALT : 0) | (ctrl ? KM_CTRL : 0) | (event.isShiftPressed() ? KM_SHIFT : 0);

                    boolean perform = KeyEvent.ACTION_DOWN == event.getAction();

                    int cursorMoving = -1;

                    switch (keyCode) {
                        case KeyEvent.KEYCODE_DPAD_UP:
                            cursorMoving = CURSOR_MOVING_UP;
                            break;
                        case KeyEvent.KEYCODE_DPAD_DOWN:
                            cursorMoving = CURSOR_MOVING_DOWN;
                            break;
                        case KeyEvent.KEYCODE_DPAD_LEFT:
                            cursorMoving = ctrl ? CURSOR_MOVING_HOME : CURSOR_MOVING_LEFT;
                            break;
                        case KeyEvent.KEYCODE_DPAD_RIGHT:
                            cursorMoving = ctrl ? CURSOR_MOVING_END : CURSOR_MOVING_RIGHT;
                            break;
                        case KeyEvent.KEYCODE_DPAD_UP_LEFT:
                            cursorMoving = CURSOR_MOVING_GLOBAL_HOME;
                            break;
                        case KeyEvent.KEYCODE_DPAD_DOWN_RIGHT:
                            cursorMoving = CURSOR_MOVING_GLOBAL_END;
                            break;
                    }

                    if (cursorMoving >= 0) {
                        res = true;
                        if (perform) {
                            onCursor(cursorMoving, 0 != (modifiers & KM_SHIFT));
                        }
                    } else {
                        int shortcut = -1;

                        if (0 == modifiers) {
                            switch (keyCode) {
                                case KeyEvent.KEYCODE_MENU:
                                case KeyEvent.KEYCODE_F10:
                                    shortcut = SHORTCUT_MAIN_MENU;
                                    break;
                                case KeyEvent.KEYCODE_F1:
                                    shortcut = SHORTCUT_TUTORIAL;
                                    break;
                                case KeyEvent.KEYCODE_F2:
                                    shortcut = SHORTCUT_SAVE_AS;
                                    break;
                                case KeyEvent.KEYCODE_F3:
                                    shortcut = SHORTCUT_OPEN;
                                    break;
                                case KeyEvent.KEYCODE_F4:
                                    shortcut = SHORTCUT_CUSTOM_FORMAT;
                                    break; // settings
                                case KeyEvent.KEYCODE_F5:
                                    shortcut = SHORTCUT_SHARE;
                                    break;

                                case KeyEvent.KEYCODE_ESCAPE:
                                    if (!onEscape()) {
                                        shortcut = SHORTCUT_CANCEL;
                                    }
                                    break;
                            }
                        } else if (KM_SHIFT == modifiers) {
                            switch (keyCode) {
                                case KeyEvent.KEYCODE_INSERT:
                                    shortcut = SHORTCUT_COPY;
                                    break;
                            }
                        } else if (KM_CTRL == modifiers) {
                            switch (keyCode) {
                                case KeyEvent.KEYCODE_S:
                                    shortcut = SHORTCUT_SAVE;
                                    break;
                                case KeyEvent.KEYCODE_O:
                                    shortcut = SHORTCUT_OPEN;
                                    break;
                                case KeyEvent.KEYCODE_X:
                                    shortcut = SHORTCUT_CUT;
                                    break;
                                case KeyEvent.KEYCODE_C:
                                    shortcut = SHORTCUT_COPY;
                                    break;
                                case KeyEvent.KEYCODE_V:
                                    shortcut = SHORTCUT_PASTE;
                                    break;
                                case KeyEvent.KEYCODE_Z:
                                    shortcut = SHORTCUT_UNDO;
                                    break;
                                case KeyEvent.KEYCODE_Y:
                                    shortcut = SHORTCUT_REDO;
                                    break;
                                case KeyEvent.KEYCODE_T:
                                    shortcut = SHORTCUT_TOGGLE_COMMENT;
                                    break;
                                case KeyEvent.KEYCODE_R:
                                    shortcut = SHORTCUT_INSERT_PREVIOUS_RESULT;
                                    break;
                                case KeyEvent.KEYCODE_N:
                                    shortcut = SHORTCUT_CLEAR;
                                    break;
                                case KeyEvent.KEYCODE_P:
                                    shortcut = SHORTCUT_CUSTOM_FORMAT;
                                    break;
                                case KeyEvent.KEYCODE_F:
                                    shortcut = SHORTCUT_FUNCTIONS;
                                    break;
                                case KeyEvent.KEYCODE_B:
                                    shortcut = SHORTCUT_BRACKETS;
                                    break;
                                case KeyEvent.KEYCODE_J:
                                    shortcut = SHORTCUT_INSERT_FRACTION;
                                    break;
                                case KeyEvent.KEYCODE_K:
                                    shortcut = SHORTCUT_INSERT_ROOT;
                                    break;
                                case KeyEvent.KEYCODE_EQUALS:
                                    shortcut = SHORTCUT_INSERT_SUBSCRIPT;
                                    break; // [Ctrl]+[=]
                            }
                        } else if ((KM_CTRL | KM_SHIFT) == modifiers) {
                            switch (keyCode) {
                                case KeyEvent.KEYCODE_NUMPAD_ADD:
                                case KeyEvent.KEYCODE_EQUALS:
                                    shortcut = SHORTCUT_INSERT_SUPERSCRIPT;
                                    break; // [Ctrl]+[Shift]+[=] or [Ctrl]+[Shift]+[+]
                            }
                        } else if (KM_ALT == modifiers) {
                            switch (keyCode) {
                                case KeyEvent.KEYCODE_DPAD_UP:
                                    shortcut = SHORTCUT_INSERT_SUPERSCRIPT;
                                    break;
                                case KeyEvent.KEYCODE_DPAD_DOWN:
                                    shortcut = SHORTCUT_INSERT_SUBSCRIPT;
                                    break;
                                case KeyEvent.KEYCODE_SLASH:
                                case KeyEvent.KEYCODE_NUMPAD_DIVIDE:
                                    shortcut = SHORTCUT_INSERT_FRACTION;
                                    break; // [Alt]+[slash]
                                case KeyEvent.KEYCODE_BACKSLASH:
                                    shortcut = SHORTCUT_INSERT_ROOT;
                                    break; // [Alt]+[back slash]
                            }
                        }

                        if (shortcut >= 0) {
                            res = true;
                            if (perform) {
                                onShortcut(shortcut);
                            }
                        }
                    }

                    mInInputContextUpdating = false;
                }

                return res;
            }

        });
    }

    private boolean onEscape() {
        // ..
        return false;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int w, int h)
    {
        if (isInEditMode() || fileListVisible)
        {
            return;
        }

        Rect r = new Rect();
        View rootview = mMainActivity.getWindow().getDecorView(); // this = activity
        rootview.getWindowVisibleDisplayFrame(r);

        int newKeyboardHeight = mInputAreaHeight - r.bottom + r.top;
        if (newKeyboardHeight < 100) {
            newKeyboardHeight = 0;
        }

        if (mKeyboardHeight != newKeyboardHeight) {
            mKeyboardHeight = newKeyboardHeight;
            updateKeyboardHeight(mKeyboardHeight);
        }
    }

    public void setClipRect(int left, int top, int width, int height) {
        mCanvas.clipRect(left, top, left + width, top + height);
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        if (isInEditMode())
        {
            return;
        }

        int width = getWidth();
        int height = getHeight();

        if ((mSettingsTop > 0) && (mSettingsBottom > height))
        {
            int dt = mSettingsBottom - height;
            mSettingsTop -= dt;
            mSettingsTop2 -= dt;
            mSettingsBottom = height;

            if (globalSettingsVisible || customFormatVisible)
            {
                showOptions(globalSettingsVisible);
            }
        }

        if ((mBitmap == null) || (mBitmap.getWidth() != width) || (mBitmap.getHeight() != height))
        {
            //mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            mCanvas = new Canvas(mBitmap);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) // >= 17 November 2012: Android 4.2, Moar jelly beans!
            {
                mBlurRenderScript = android.renderscript.RenderScript.create(mMainActivity);
                mBlurOverlayAlloc = android.renderscript.Allocation.createFromBitmap(mBlurRenderScript, mBitmap);
                mBlur = android.renderscript.ScriptIntrinsicBlur.create(mBlurRenderScript, mBlurOverlayAlloc.getElement());
            }
        }

        Rect cliprect = canvas.getClipBounds();
        mRectForInvalidate.top = mRectForInvalidate.left = mRectForInvalidate.right = mRectForInvalidate.bottom = 0;
        if (renderInputArea(mCanvas, 0, 0, width, height, cliprect.left, cliprect.top, cliprect.width(), cliprect.height(), false))
        {
            postDelayed(new Runnable() { @Override public void run() { animateTutorial(); } }, 12); // animate tutorial sliding
        }

        canvas.drawBitmap(mBitmap, 0, 0, null);
    }

    // callbacks (draw primitives)
    public void releaseResources() {
        mFonts.clear();
    }

    public int createFont(String name, float height, boolean bold, boolean italic, int fontIndex)
    {
        if (fontIndex < 0)
        {
            Paint font = new Paint();
            font.setAntiAlias(true);
            font.setSubpixelText(true);
            font.setStyle(Paint.Style.FILL);

            fontIndex = mFonts.size();
            mFonts.add(font);
        }

        if (fontIndex < mFonts.size())
        {
            Paint font = mFonts.get(fontIndex);
            int style = (bold ? Typeface.BOLD : 0) | (italic ? Typeface.ITALIC : 0);
            font.setTypeface(Typeface.create(name, style));
            font.setTextSize(height);
        }

        return fontIndex;
    }

    public int createFont(char[] name, int count, float height, boolean bold, boolean italic, int fontIndex)
    {
        return createFont(new String(name, 0, count), height, bold, italic, fontIndex);
    }

    public int createFont(int fontFamilyIndex, float height, boolean bold, boolean italic, int fontIndex)
    {
        return createFont(1 == fontFamilyIndex ? "serif" : 2 == fontFamilyIndex ? "casual" : "sans-serif", height, bold, italic, fontIndex);
    }

    public void setSmoothingMode(boolean antiAlias)
    {
        mCommonPaint.setAntiAlias(antiAlias);
    }

    static final float FONT_HEIGHT_FACTOR = 0.900f;
    static final float FONT_OFFSET_FACTOR = 0.750f;

    public float getFontHeight(int fontIndex) {
        float res = 0;

        // see: https://stackoverflow.com/questions/3654321/measuring-text-height-to-be-drawn-on-canvas-android

        if (fontIndex < mFonts.size()) {
            Paint font = mFonts.get(fontIndex);
            Paint.FontMetrics fm = font.getFontMetrics();
            res = fm.bottom - fm.top + fm.leading; // fm.descent - fm.ascent
            font.setStrokeMiter(res * FONT_OFFSET_FACTOR);
        }

        return res * FONT_HEIGHT_FACTOR;
    }

    public float getStringWidth(char[] s, int count, int fontIndex) {
        float res = 0;

        if (fontIndex < mFonts.size()) {
            Paint font = mFonts.get(fontIndex);
            res = font.measureText(s, 0, count);
        }

        return res;
    }

    public float getCharWidth(char c, int fontIndex) {
        float res = 0;

        if (fontIndex < mFonts.size()) {
            mChar[0] = c;
            Paint font = mFonts.get(fontIndex);
            res = font.measureText(mChar, 0, 1);
        }

        return res;
    }

    private static final float r2g = 180.0f / 3.1415926535898f;

    public void applyAndReleaseDrawingMatrix(float x0, float y0, float angle, float mx, float my) {
        mCanvas.save();

        if ((0.0f != x0) || (0.0f != y0)) {
            mCanvas.translate(x0, y0);
        }

        if (0.0f != angle) {
            mCanvas.rotate(angle * r2g);
        }

        if ((1.0f != mx) || (1.0f != my)) {
            mCanvas.scale(mx, my);
        }
    }

    public void restorePreviousDrawingMatrix() {
        mCanvas.restore();
    }


    public void fillRectangle(int brush, int x0, int y0, int width, int height) {
        fillRectangle(brush, (float) x0, (float) y0, (float) width, (float) height);
    }

    public void fillRectangle(int brush, float x0, float y0, float width, float height) {
        mCommonPaint.setColor(brush);
        mCanvas.drawRect(x0, y0, x0 + width, y0 + height, mCommonPaint);
    }

    public void drawRectangle(int pen, float penWidth, float x0, float y0, float width, float height) {
        mCommonPaint.setColor(pen);
        mCommonPaint.setStrokeWidth(penWidth);

        float x1 = x0 + width;
        float y1 = y0 + height;

        mPoints[0] = x0;
        mPoints[1] = y0;
        mPoints[2] = x1;
        mPoints[3] = y0;
        mPoints[4] = x1;
        mPoints[5] = y0;
        mPoints[6] = x1;
        mPoints[7] = y1;
        mPoints[8] = x1;
        mPoints[9] = y1;
        mPoints[10] = x0;
        mPoints[11] = y1;
        mPoints[12] = x0;
        mPoints[13] = y1;
        mPoints[14] = x0;
        mPoints[15] = y0;
        mCanvas.drawLines(mPoints, mCommonPaint);
    }

    public void fillEllipse(int brush, float x0, float y0, float width, float height) {
        mCommonPaint.setColor(brush);

        float r = width * 0.5f;
        mCanvas.drawCircle(x0 + r, y0 + r, r, mCommonPaint);
    }

    public void drawEllipse(int pen, float penWidth, float x0, float y0, float width, float height) {
        Paint.Style ps = mCommonPaint.getStyle();
        mCommonPaint.setStyle(Paint.Style.STROKE);

        mCommonPaint.setColor(pen);
        mCommonPaint.setStrokeWidth(penWidth);

        float r = width * 0.5f;
        mCanvas.drawCircle(x0 + r, y0 + r, r, mCommonPaint);

        mCommonPaint.setStyle(ps);
    }

    public void drawLine(int pen, float penWidth, int x1, int y1, int x2, int y2) {
        drawLine(pen, penWidth, (float) x1, (float) y1, (float) x2, (float) y2);
    }

    public void drawLine(int pen, float penWidth, float x1, float y1, float x2, float y2) {
        mCommonPaint.setColor(pen);
        mCommonPaint.setStrokeWidth(penWidth);
        mCanvas.drawLine(x1, y1, x2, y2, mCommonPaint);
    }

    public void drawLineWithFlatCaps(int pen, float penWidth, int x1, int y1, int x2, int y2) {
        mCommonPaint.setColor(pen);
        mCommonPaint.setStrokeWidth(penWidth);
        mCommonPaint.setStrokeCap(Paint.Cap.BUTT);
        mCanvas.drawLine((float)x1, (float)y1, (float)x2, (float)y2, mCommonPaint);
        mCommonPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void drawLines(int pen, float penWidth, int x1, int y1, int x2, int y2, int x3, int y3) {
        mCommonPaint.setColor(pen);
        mCommonPaint.setStrokeWidth(penWidth);
        mPoints[0] = x1;
        mPoints[1] = y1;
        mPoints[2] = x2;
        mPoints[3] = y2;
        mPoints[4] = x2;
        mPoints[5] = y2;
        mPoints[6] = x3;
        mPoints[7] = y3;
        mCanvas.drawLines(mPoints, 0, 8, mCommonPaint);
    }

    public void drawLines(int pen, float penWidth, int x1, int y1, int x2, int y2, int x3, int y3, int x4, int y4, int x5, int y5) {
        mPoints[0] = x1;
        mPoints[1] = y1;
        mPoints[2] = x2;
        mPoints[3] = y2;
        mPoints[4] = x2;
        mPoints[5] = y2;
        mPoints[6] = x3;
        mPoints[7] = y3;
        mPoints[8] = x3;
        mPoints[9] = y3;
        mPoints[10] = x4;
        mPoints[11] = y4;
        mPoints[12] = x4;
        mPoints[13] = y4;
        mPoints[14] = x5;
        mPoints[15] = y5;
        mCommonPaint.setColor(pen);
        mCommonPaint.setStrokeWidth(penWidth);
        mCanvas.drawLines(mPoints, mCommonPaint);
    }

    public void drawString(char[] s, int index, int count, Paint font, int brush, float x, float y) {
        font.setColor(brush);
        mCanvas.drawText(s, index, count, x, y + font.getStrokeMiter(), font);
    }

    public void drawString(char[] s, int index, int count, Paint font, int brush, float x, float y, float width, boolean atCenter) {
        //..
    }

    public void drawString(char[] s, int count, int fontIndex, int brush, float x, float y, float width, float height, boolean atCenter) {
        if (fontIndex < mFonts.size()) {
            Paint font = mFonts.get(fontIndex);
            float stringHeight = font.getStrokeMiter();

            int stringCount = 1;
            int prevLineBegin = 0;
            mStringAttributes[0] = 0;
            mStringAttributes[1] = count;

            for (int i = 0; i < count; ++i) {
                if ('\n' == s[i]) {
                    int stringAttributesIndex = stringCount * 2;
                    mStringAttributes[stringAttributesIndex - 1] = i - prevLineBegin; // prev string length

                    mStringAttributes[stringAttributesIndex] = i + 1;         // the string begin
                    mStringAttributes[stringAttributesIndex + 1] = count - i - 1; // the string length

                    if (++stringCount >= STRING_ATTRIBUTES_SIZE) {
                        break;
                    }
                }
            }

            if (atCenter) {
                x += width * 0.5f;
                font.setTextAlign(Paint.Align.CENTER);

                if (height > 0.0f) // calculate top space
                {
                    y += (height - stringHeight * (float) stringCount * 1.10f) * 0.475f;
                }
            }

            for (int i = 0; i < stringCount; ++i) {
                int stringAttributesIndex = i * 2;
                int stringBegin = mStringAttributes[stringAttributesIndex];
                int stringLength = mStringAttributes[stringAttributesIndex + 1];

                drawString(s, stringBegin, stringLength, font, brush, x, y);
                y += stringHeight;
            }

            font.setTextAlign(Paint.Align.LEFT);
        }
    }

    public void drawString(char[] s, int count, int fontIndex, int brush, float x, float y) {
        drawString(s, count, fontIndex, brush, x, y, 0.0f, 0.0f, false);
    }

    public void drawString(char[] s, int count, int fontIndex, int brush, int x, int y) {
        drawString(s, count, fontIndex, brush, (float) x, (float) y, 0.0f, 0.0f, false);
    }

    public void drawString(char[] s, int count, int fontIndex, int brush, int left, int top, int width, int height) {
        drawString(s, count, fontIndex, brush, (float) left, (float) top, (float) width, (float) height, false);
    }

    public void drawStringAtCenter(char[] s, int count, int fontIndex, int brush, int left, int top, int width, int height) {
        drawString(s, count, fontIndex, brush, (float) left, (float) top, (float) width, (float) height, true);
    }

    public void drawStringAtCenterHorz(char[] s, int count, int fontIndex, int brush, float x, float y, float width) {
        drawString(s, count, fontIndex, brush, x, y, width, 0.0f, true);
    }

    public void drawChar(char c, int fontIndex, int brush, float x, float y) {
        if (fontIndex < mFonts.size()) {
            mChar[0] = c;
            drawString(mChar, 0, 1, mFonts.get(fontIndex), brush, x, y);
        }
    }

    public Path createGraphicsPath() {
        return new Path();
    }

    public void addLine(Path path, float x1, float y1, float x2, float y2) {
        if (path.isEmpty()) {
            path.moveTo(x1, y1);
        }

        path.lineTo(x2, y2);
    }

    public void addArc(Path path, float x1, float y1, float x2, float y2, float r) {
        if (path.isEmpty()) {
            path.moveTo(x1, y1);
        }

        float r_factor = 0.5457875f;
        float dx = (x2 - x1) * r_factor;
        float dy = (y2 - y1) * r_factor;

        if ((dx * dy) > 0.0f) {
            path.cubicTo(x1, y1 + dy, x2 - dx, y2, x2, y2);
        }
        else {
            path.cubicTo(x1 + dx, y1, x2, y2 - dy, x2, y2);
        }
    }

    public void addArc(Path path, float x0, float y0, float width, float height, float angleBegin, float angleEnd) {
    }

    public void addBezier(Path path, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4) {
        if (path.isEmpty()) {
            path.moveTo(x1, y1);
        }

        path.cubicTo(x2, y2, x3, y3, x4, y4);
    }

    public void fillPath(Path path, int brush)
    {
        if (!path.isEmpty()) {
            mCommonPaint.setColor(brush);
            mCanvas.drawPath(path, mCommonPaint);
        }
    }

    public void drawPath(Path path, int pen, float penWidth)
    {
        if (!path.isEmpty()) {
            Paint.Style ps = mCommonPaint.getStyle();
            mCommonPaint.setStyle(Paint.Style.STROKE);

            mCommonPaint.setColor(pen);
            mCommonPaint.setStrokeWidth(penWidth);
            mCanvas.drawPath(path, mCommonPaint);

            mCommonPaint.setStyle(ps);
        }
    }

    public void drawBitmap(int bitmapIndex, int x, int y)
    {
        //..
    }

    public void setAlpha(float alpha)
    {
        int alpha_ = (int) (alpha * 255);
        if (alpha_ > 255) {
            alpha_ = 255;
        }

        mCommonPaint.setAlpha(alpha_);
    }

    private android.renderscript.RenderScript mBlurRenderScript = null;
    private android.renderscript.Allocation mBlurOverlayAlloc = null;
    private android.renderscript.ScriptIntrinsicBlur mBlur = null;

    private static native boolean blurBitmap(Bitmap  bitmap);
    public boolean blurPopupBackground()
    {
        return blurBackground(25.0f, true);
    }

    public boolean blurBackground(float r, boolean fillIfCannotBlur)
    {
        //return blurBitmap(mBitmap); -- use implementation in c++ (when it become fast enough)

        boolean res = false;
        try
        {
            if ((null == mBlurRenderScript) || (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1)) // see <mBlur> initialization in init() above
            {
                if (fillIfCannotBlur)
                {
                    fillRectangle(0xCCFFFFFF, 0, 0, getWidth(), getHeight());
                }
            }
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) // to avoid warning: call requires API level 17 (current min is 15): ..
            {
                mBlurOverlayAlloc = android.renderscript.Allocation.createFromBitmap(mBlurRenderScript, mBitmap);
                mBlur = android.renderscript.ScriptIntrinsicBlur.create(mBlurRenderScript, mBlurOverlayAlloc.getElement());

                mBlur.setInput(mBlurOverlayAlloc);
                mBlur.setRadius(r);
                mBlur.forEach(mBlurOverlayAlloc);
                //mBlur.forEach(mBlurOverlayAlloc); too slow(
                mBlurOverlayAlloc.copyTo(mBitmap);
            }

            res = true;
        }
        catch (Throwable e)
        {
            e.printStackTrace();
        }

        return res;
    }

    private class TutorialPage
    {
        public void clear()
        {
            mPageId = -1;
            mBitmap = null;
        }

        public Bitmap capturePage(int pageWidth, int pageHeight, int pageId)
        {
            mBitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888);

            mCanvas = new Canvas(mBitmap);
            captureTutorialPage(pageWidth, pageHeight, pageId);

            return mBitmap;
        }

        public boolean match(int pageWidth, int pageHeight, int pageId)
        {
            return (null != mBitmap) && (pageId == mPageId) && (pageWidth == mBitmap.getWidth()) && (pageHeight == mBitmap.getHeight());
        }

        public int mPageId;
        public Bitmap mBitmap;

    };

    private int mCurrentPageId;
    private ArrayList<TutorialPage> mTutorialCache;

    public void resetTutorialStuff()
    {
        mCurrentPageId = -1;

        if (null == mTutorialCache)
        {
            mTutorialCache = new ArrayList<TutorialPage>(3);
            for (int i = 0; i < 3; ++i) mTutorialCache.add(new TutorialPage());
        }

        for (TutorialPage page : mTutorialCache) page.clear();
    }

    private native int getPrevTutorialPageId(int pageId);
    private native int getNextTutorialPageId(int pageId);

    private void cacheTutorialPageInAdvance()
    {
        if (mCurrentPageId >= 0)
        {
            for (TutorialPage currentPage : mTutorialCache)
            {
                if (currentPage.mPageId == mCurrentPageId)
                {
                    int prevPageId = getPrevTutorialPageId(mCurrentPageId);
                    int nextPageId = getNextTutorialPageId(mCurrentPageId);

                    boolean maybePrev = prevPageId != mCurrentPageId;
                    boolean maybeNext = nextPageId != mCurrentPageId;

                    for (TutorialPage page : mTutorialCache)
                    {
                        int id = page.mPageId;
                        if (id == prevPageId) maybePrev = false;
                        if (id == nextPageId) maybeNext = false;
                    }

                    if (maybePrev || maybeNext)
                    {
                        for (TutorialPage page : mTutorialCache)
                        {
                            boolean maybePrev_ = maybePrev && (page.mPageId != prevPageId);
                            boolean maybeNext_ = maybeNext && (page.mPageId != nextPageId);
                            if ((page.mPageId != mCurrentPageId) && (maybePrev_ || maybeNext_))
                            {
                                cacheTutorialPage(currentPage.mBitmap.getWidth(), currentPage.mBitmap.getHeight(), maybePrev_ ? prevPageId : nextPageId);
                                break;
                            }
                        }
                    }

                    break;
                }
            }
        }
    }

    private Bitmap cacheTutorialPage(int pageWidth, int pageHeight, int pageId)
    {
        Bitmap res = null;

        TutorialPage thePage = null;

        int candidateIdDelta = 0;
        TutorialPage pageCandidate = null;

        if (null == mTutorialCache)
        {
            resetTutorialStuff();
        }

        for (TutorialPage page : mTutorialCache)
        {
            if ((null == page.mBitmap) || (page.mPageId == pageId) || (page.mBitmap.getWidth() != pageWidth) || (page.mBitmap.getHeight() != pageHeight))
            {
                thePage = page;
                break;
            }

            int idDelta = Math.abs(pageId - page.mPageId);
            if (idDelta > candidateIdDelta)
            {
                candidateIdDelta = idDelta;
                pageCandidate = page;
            }
        }

        if (null == thePage)
        {
            thePage = pageCandidate;
        }

        if (null == thePage)
        {
            thePage = mTutorialCache.get(0);
        }

        if (null != thePage)
        {
            Canvas orgDrawContext = mCanvas;

            try
            {
                Bitmap bm = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888);

                mCanvas = new Canvas(bm);
                captureTutorialPage(pageWidth, pageHeight, pageId);

                thePage.mPageId = pageId;
                thePage.mBitmap = bm;

                res = bm;
            }
            catch (Throwable t)
            {
                t.printStackTrace();
            }

            mCanvas = orgDrawContext;
        }

        return res;
    }

    public void drawTutorialPageBitmap(int left, int top, int pageWidth, int pageHeight, int pageId)
    {
        boolean drawn = false;

        mCurrentPageId = -1;

        try
        {
            pageWidth += 2;
            pageHeight += 2;

            Bitmap bm = null;
            for (TutorialPage page : mTutorialCache)
            {
                if (page.match(pageWidth, pageHeight, pageId))
                {
                    bm = page.mBitmap;
                    break;
                }
            }

            if (null == bm)
            {
                bm = cacheTutorialPage(pageWidth, pageHeight, pageId);
            }

            mCanvas.drawBitmap(bm, left, top, null);

            if ((0 == left) && (0 == top))
            {
                mCurrentPageId = pageId;
                post(new Runnable() { @Override public void run() { cacheTutorialPageInAdvance(); } });
            }

            drawn = true;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public void drawScreenshot(float x0, float y0, int screenshotIndex, boolean forTablet, int pen)
    {
        try
        {
            int ssId = -1;

            {
                // ssId = ..
            }

            if (ssId >= 0)
            {
                Bitmap ss = android.graphics.BitmapFactory.decodeResource(getResources(), ssId);

                int dd = ss.getDensity();
                int m = dd * 100 / 160; // multiplier in percents
                ss.setDensity(dd * m / 100);

                Paint paint = new Paint();
                paint.setAntiAlias(true);

                if (screenshotIndex < 14)
                {
                    drawRectangle(pen, 2.0f, x0, y0, ss.getWidth() * 100 / m, ss.getHeight() * 100 / m);
                }

                mCanvas.drawBitmap(ss, x0, y0, paint);
            }

        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    private boolean drawBannerWithGetButton = false;
    private Bitmap vceBannerBackgroung = null;
    private void resetVceBannerStuff()
    {
        vceBannerBackgroung = null;
    }

    private void drawVceBannerBackground(int brush, int left_i, int top_i, int width_i, int height_i, boolean bannerWithGetButton)
    {
        Canvas orgDrawContext = mCanvas;

        try
        {
            if ((null == vceBannerBackgroung) || (vceBannerBackgroung.getWidth() != width_i) || (vceBannerBackgroung.getHeight() != height_i) || (drawBannerWithGetButton != bannerWithGetButton))
            {
                vceBannerBackgroung = null;
                if ((width_i > 0) && (height_i > 0))
                {
                    //Bitmap vceBannerBackgroung = Bitmap.createBitmap(width_i, height_i, Bitmap.Config.ARGB_565);
                    vceBannerBackgroung = Bitmap.createBitmap(width_i, height_i, Bitmap.Config.ARGB_8888);
                    mCanvas = new Canvas(vceBannerBackgroung);
                    prepareVceBannerBackground(brush, width_i, height_i, bannerWithGetButton);
                    drawBannerWithGetButton = bannerWithGetButton;
                }
            }
        }
        catch (Throwable t)
        {
            t.printStackTrace();
        }

        mCanvas = orgDrawContext;

        if (null != vceBannerBackgroung)
        {
            mCanvas.drawBitmap(vceBannerBackgroung, left_i, top_i, null);
            //mCanvas.drawBitmap(vceBannerBackgroung, left_i, top_i, mCommonPaint);
        }
    }

//*****************************************************************************
// uk::ui

    private static native void setXStuffRect(int top, int left, int right, int bottom);

    public static native void showEditor();
    private void backToEditor()
    {
        onClosePopup();
        showEditor();
    }

    private static native boolean shareAs(String fileName);
    private static native boolean open(String fileName, boolean inLocalStorage);
    private static native boolean saveAs(String fileName, boolean inLocalStorage);
    private static native boolean remove(String fileName, boolean inLocalStorage);

    private static native void onShowModal();

    public void showMainMenu(boolean visible)
    {
        //TODO: hide keyboard
    }

    public void onClosePopup()
    {
        resetTutorialStuff();

        setXStuffRect(0, 0, 0, 0);

        fileListVisible = false;
        mFilesView.setVisibility(INVISIBLE);

        customFormatVisible = false;
        globalSettingsVisible = false;
        mSettingsView.setVisibility(INVISIBLE);

        if (mMainActivity.mTurnOffFullscreen)
        {
            mMainActivity.mTurnOffFullscreen = false;
            mMainActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    private static boolean reshowAlertDialog = false;
    private static String alertDialogTitle = "";
    private static String alertDialogMessage = "";
    private static int alertDialogIcon = -1;
    private static boolean alertDialogCancellable = false;
    private static OnClickListenerEx alertDialogOnNegativeButtonClick = null;
    private static OnClickListenerEx alertDialogOnPositiveButtonClick = null;
    private static OnClickListenerEx alertDialogOnNeutralButtonClick = null;
    private static String alertDialogNegativeButtonTitle = "";
    private static String alertDialogPositiveButtonTitle = "";
    private static String alertDialogNeutralButtonTitle = "";


    public static void onCloseAlertDialog() {
        reshowAlertDialog = false;
    }

    public class OnClickListenerEx implements DialogInterface.OnClickListener {
        public void onClick() {
        } // must be overridden

        @Override
        public void onClick(DialogInterface dialog, int id) {
            onClick();
            onCloseAlertDialog();
        }
    }

    public void reshowAlertDialog()
    {
        if (reshowAlertDialog)
        {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(mMainActivity);

            builder.setIconAttribute(alertDialogIcon);
            builder.setTitle(alertDialogTitle);
            builder.setMessage(alertDialogMessage);

            builder.setCancelable(alertDialogCancellable);
            builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    onCloseAlertDialog();
                }
            });

            if (null != alertDialogOnNegativeButtonClick) {
                builder.setNegativeButton(alertDialogNegativeButtonTitle, alertDialogOnNegativeButtonClick);
            }
            if (null != alertDialogOnPositiveButtonClick) {
                builder.setPositiveButton(alertDialogPositiveButtonTitle, alertDialogOnPositiveButtonClick);
            }
            if (null != alertDialogOnNeutralButtonClick) {
                builder.setNeutralButton(alertDialogNeutralButtonTitle, alertDialogOnNeutralButtonClick);
            }

            builder.show();
        }
    }

    private void showAlertDialog(int alertDialogIcon_, String alertDialogTitle_, String alertDialogMessage_, boolean alertDialogCancellable_,
                                 String alertDialogNegativeButtonTitle_, OnClickListenerEx alertDialogOnNegativeButtonClick_,
                                 String alertDialogPositiveButtonTitle_, OnClickListenerEx alertDialogOnPositiveButtonClick_,
                                 String alertDialogNeutralButtonTitle_, OnClickListenerEx alertDialogOnNeutralButtonClick_) {
        reshowAlertDialog = true;

        alertDialogIcon = alertDialogIcon_;
        alertDialogTitle = alertDialogTitle_;
        alertDialogMessage = alertDialogMessage_;
        alertDialogCancellable = alertDialogCancellable_;

        alertDialogNegativeButtonTitle = alertDialogNegativeButtonTitle_;
        alertDialogOnNegativeButtonClick = alertDialogOnNegativeButtonClick_;
        alertDialogPositiveButtonTitle = alertDialogPositiveButtonTitle_;
        alertDialogOnPositiveButtonClick = alertDialogOnPositiveButtonClick_;
        alertDialogNeutralButtonTitle = alertDialogNeutralButtonTitle_;
        alertDialogOnNeutralButtonClick = alertDialogOnNeutralButtonClick_;

        reshowAlertDialog();
    }



    // *********************************************************************************************
    // open file dialog:
    // https://habrahabr.ru/post/203884/
    // http://www.wtg.ru/products/onlinedev/031/
    // https://stackoverflow.com/questions/3592717/choose-file-dialog


    private void reshowFileList()
    {
        post(new Runnable()
        {
            @Override
            public void run()
            {
                showFileList(inOpenFile, inBeforeOpenFile, true);
            }
        });
    }

    void trySaveAs(String fileName)
    {
        if (saveAs(fileName, true))
        {
            backToEditor();
        }
        else
        {
            reshowFileList();
        }
    }

    private static String existingFileName = "";
    private void onOverwrite(String fileName, String info)
    {
        existingFileName = fileName;

        int infoSplitIndex = info.indexOf(FILE_ATTRIBUTES_SEPARATOR);
        if ((infoSplitIndex < 0) || (infoSplitIndex > (info.length() - 5)))
        {
            info = "?   ?";
            infoSplitIndex = 1;
        }

        showAlertDialog(android.R.attr.alertDialogIcon, "Replace", "'" + existingFileName + "'\nsaved on " + info.substring(infoSplitIndex + 3), true, // + "\nsize: " + info.substring(0, infoSplitIndex), false,
                "Cancel", new OnClickListenerEx(), "Ok", new OnClickListenerEx() { @Override public void onClick() { trySaveAs(existingFileName); } }, "", null);
    }

    private static final String TITLE = "filename"; // 'main' text
    private static final String DESCRIPTION = "fileinfo"; // below the name
    private static final String ICON_HIGHLIGHTED = "filehighlighted";
    private static final String TITLE_HIGHLIGHTED = "filenamehighlighted";
    private static final String DESCRIPTION_HIGHLIGHTED = "fileinfohighlighted";
    private static final String SELECTED_TOKEN = "selected"; // invisible boolean
    private static final String mIconHighlighted = new String(new char[]{0x2713}); // 2BC1, 2666: diamonds; 20DF, 23FA, 23F9: circles; 2BC0: square; 2713, 2714: check; 269D, 2739, 2605: stars

    public static ArrayList<HashMap<String, Object>> mFileList = new ArrayList<HashMap<String, Object>>();

    //https://www.mindstick.com/Articles/1577/android-delete-multiple-selected-items-in-listview
    AdapterView.OnItemClickListener itemClickListener = new AdapterView.OnItemClickListener()
    {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id)
        {
            if (inSelectFiles)
            {
                mFileListView.setItemChecked(position, !mFileListView.isItemChecked(position));
                updateSelectedFileCount();
            }
            else
            {
                //https://habrahabr.ru/post/154931/
                //.. a litle lifehack to show a lot of items in ListView

                HashMap<String, Object> itemHashMap = (HashMap<String, Object>) parent.getItemAtPosition(position);
                String fileName = itemHashMap.get(TITLE).toString() + itemHashMap.get(TITLE_HIGHLIGHTED).toString();

                if (inOpenFile)
                {
                    open(fileName, true);
                    backToEditor();
                }
                else
                {
                    onOverwrite(fileName, itemHashMap.get(DESCRIPTION).toString() + itemHashMap.get(DESCRIPTION_HIGHLIGHTED).toString());
                }
            }
        }
    };

    AdapterView.OnItemLongClickListener itemLongClickListener = new AdapterView.OnItemLongClickListener()
    {
        @Override
        public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id)
        {
            if (!inSelectFiles)
            {
                toggleFileSelection(true);
                mFileListView.setItemChecked(position, true);
                updateSelectedFileCount();
            }

            return true;
        }
    };

    AbsListView.OnScrollListener scrollListener = new AbsListView.OnScrollListener()
    {
        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount)
        {
            mFileListTopItem = firstVisibleItem;
        }

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState)
        {
        }
    };

    static boolean fileListVisible = false;
    static boolean inSelectFiles = false;
    static boolean inOpenFile = false;
    static boolean inBeforeOpenFile = false;
    static boolean inNewFileNameInput = false;
    static Integer mSelectedFileCount = 0;
    static String mSelectFilesInfo = "";
    static int mFileListTopItem = 0;

    public void reshowUi()
    {
        if (fileListVisible)
        {
            showFileList(inOpenFile, inBeforeOpenFile, false);
        }
        else if (globalSettingsVisible || customFormatVisible)
        {
            showOptions(globalSettingsVisible);
        }
        else if (!mShareFileName.isEmpty())
        {
            showShareStuff();
        }
    }

    static EditText mNewFileNameEditText = null;
    android.app.AlertDialog mNewFileNameDialog = null;
    boolean mNewFileNameIsValid = false;
    String mNewFileName;

    private void onNewFileNameChanged()
    {
        if (null != mNewFileNameDialog)
        {
            Button saveButton = mNewFileNameDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (null != saveButton)
            {
                saveButton.setEnabled(mNewFileNameIsValid);
            }
        }
    }

    public void showNewFileInteface(boolean clearFileName)
    {
        inNewFileNameInput = true;

        int newFileNameSelectionStart = 0;
        int newFileNameSelectionStop = 0;
        String newFileName = "";
        if (!clearFileName && (null != mNewFileNameEditText))
        {
            newFileNameSelectionStart = mNewFileNameEditText.getSelectionStart();
            newFileNameSelectionStop = mNewFileNameEditText.getSelectionEnd();
            newFileName = mNewFileNameEditText.getText().toString();
        }

        mNewFileNameEditText = new EditText(mMainActivity);
        mNewFileNameEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {} // Auto-generated method stub

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {} // Auto-generated method stub

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {
                StringBuilder newFileName = new StringBuilder(mNewFileNameEditText.getText().toString());
                int newFileNameTextLength = newFileName.length();

                mNewFileNameIsValid = newFileNameTextLength > 0;
                for (int i = 0; mNewFileNameIsValid && (i < newFileNameTextLength); ++i)
                {
                    final char c = newFileName.charAt(i);
                    mNewFileNameIsValid = '/' != c;
                }

                onNewFileNameChanged();
            }

        });

        mNewFileNameEditText.setSingleLine();
        mNewFileNameEditText.setHint("Enter a name for this calculation");
        mNewFileNameEditText.setText(newFileName.toCharArray(), 0, newFileName.length());
        mNewFileNameEditText.setSelection(newFileNameSelectionStart, newFileNameSelectionStop);

        android.app.AlertDialog.Builder alertDialogBuilder = new android.app.AlertDialog.Builder(mMainActivity);

        LinearLayout layout = new LinearLayout(mMainActivity);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(params);

        layout.setGravity(android.view.Gravity.CLIP_VERTICAL);
        layout.setPadding(2, 2, 2, 2);

        int horzMargin = 20;
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.topMargin = 5;
        layoutParams.leftMargin = horzMargin;
        layoutParams.rightMargin = horzMargin;
        layoutParams.bottomMargin = horzMargin; // 15;
        layout.addView(mNewFileNameEditText, layoutParams);

        mNewFileName = "";
        alertDialogBuilder.setView(layout);
        alertDialogBuilder.setTitle("New Calculation");
        alertDialogBuilder.setCancelable(false);

        alertDialogBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int whichButton)
            {
                inNewFileNameInput = false;
                dialog.cancel();
            }
        });

        alertDialogBuilder.setPositiveButton("Save", new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int whichButton)
            {
                inNewFileNameInput = false;
                mNewFileName = mNewFileNameEditText.getText().toString();

                int fileCount = mFileList.size();
                for (int i = 0; i < fileCount; ++i)
                {
                    HashMap<String, Object> itemHashMap = mFileList.get(i);
                    String fileName = itemHashMap.get(TITLE).toString();
                    if (mNewFileName.equals(fileName))
                    {
                        onOverwrite(fileName, itemHashMap.get(DESCRIPTION).toString() + itemHashMap.get(DESCRIPTION_HIGHLIGHTED).toString());
                        mNewFileName = "";
                        break;
                    }
                }

                if (!mNewFileName.isEmpty())
                {
                    trySaveAs(mNewFileName);
                }

            }
        });

        mNewFileNameDialog = alertDialogBuilder.create();
        mNewFileNameDialog.show();
        onNewFileNameChanged();

        mMainActivity.showKeyboard(true);
    }

    Runnable setFocusToNewFileEditText = new Runnable()
    {
        @Override
        public void run()
        {
            mNewFileNameEditText.requestFocus();
        }
    };

    private static native void enumLocalFiles(boolean excludeLocalSamples);

    private final static String FILE_ATTRIBUTES_SEPARATOR = " – ";
    private String fileSizeToString(int size)
    {
        int m = 1;
        while ((size > 1024) && (m < 4))
        {
            ++m;
            size = size / 1024;
        }

        Integer size_ = size;
        return size_.toString() + (1 == m ? " B" : 2 == m ? " KB" : 3 == m ? " MB" : " GB");
    }

    public void addFileToList(char[] path, int pathSize, int fileSize, int dateInSecs, boolean highlighted)
    {
        HashMap<String, Object> hm = new HashMap<>();
        Date date = new Date(((long) dateInSecs) * 1000);

        if (highlighted)
        {
            hm.put(ICON_HIGHLIGHTED, mIconHighlighted);
            hm.put(TITLE_HIGHLIGHTED, new String(path, 0, pathSize));
            hm.put(DESCRIPTION_HIGHLIGHTED, fileSizeToString(fileSize) + FILE_ATTRIBUTES_SEPARATOR + mDateFormat.format(date));

            hm.put(TITLE, "");
            hm.put(DESCRIPTION, "");
        }
        else
        {
            hm.put(TITLE, new String(path, 0, pathSize));
            hm.put(DESCRIPTION, fileSizeToString(fileSize) + FILE_ATTRIBUTES_SEPARATOR + mDateFormat.format(date));

            hm.put(TITLE_HIGHLIGHTED, "");
            hm.put(DESCRIPTION_HIGHLIGHTED, "");
        }

        mFileList.add(hm);
    }

    class FileListEntryComparator implements Comparator<HashMap<String, Object>> {
        public int compare(HashMap<String, Object> A, HashMap<String, Object> B) {
            String A_ = A.get(TITLE).toString();
            if (A_.isEmpty()) {
                A_ = A.get(TITLE_HIGHLIGHTED).toString();
            }
            String B_ = B.get(TITLE).toString();
            if (B_.isEmpty()) {
                B_ = B.get(TITLE_HIGHLIGHTED).toString();
            }
            return A_.compareToIgnoreCase(B_);
        }
    }

    public void updateFileView()
    {
        mMainActivity.filesMenu.findItem(R.id.action_location).setVisible(false);

        MenuItem mMenuItemAddNewFile = mMainActivity.filesMenu.findItem(R.id.action_newfile);
        MenuItem mMenuItemSelectFiles = mMainActivity.filesMenu.findItem(R.id.action_select_files);
        MenuItem mMenuItemSelectAll = mMainActivity.filesMenu.findItem(R.id.action_select_all);
        MenuItem mMenuItemUnselectAll = mMainActivity.filesMenu.findItem(R.id.action_unselect_all);
        MenuItem mMenuItemDeleteFiles = mMainActivity.filesMenu.findItem(R.id.action_multiple_delete);
        MenuItem mMenuItemCancelSelectionMode = mMainActivity.filesMenu.findItem(R.id.action_cancel_selection_mode);

        if (inSelectFiles)
        {
            mMainActivity.mFilesToolbarLight.setTitle(mSelectFilesInfo);
            mMainActivity.mFilesToolbar.setTitle(mSelectFilesInfo);
            mMenuItemSelectFiles.setVisible(false);
            mMenuItemAddNewFile.setVisible(false);
            mMenuItemDeleteFiles.setVisible(true);
            mMenuItemCancelSelectionMode.setVisible(true);

            boolean filesSelected = mSelectedFileCount > 0;
            mMenuItemDeleteFiles.setEnabled(filesSelected);
            mMenuItemDeleteFiles.getIcon().setAlpha(filesSelected ? 255 : 64);
        }
        else
        {
            mMainActivity.mFilesToolbarLight.setTitle(inOpenFile ? "Open" : "Save As");
            mMainActivity.mFilesToolbar.setTitle(mMainActivity.mFilesToolbarLight.getTitle());
            mMenuItemAddNewFile.setVisible(!inOpenFile);

            mMenuItemSelectFiles.setVisible(true);
            mMenuItemDeleteFiles.setVisible(false);
            mMenuItemSelectAll.setVisible(false);
            mMenuItemUnselectAll.setVisible(false);
            mMenuItemCancelSelectionMode.setVisible(false);
        }
    }

    public void updateSelectedFileCount()
    {
        mSelectedFileCount = 0;
        for (int i = mFileListView.getCount(); i > 0; )
        {
            if (mFileListView.isItemChecked(--i))
            {
                ++mSelectedFileCount;
                mFileList.get(i).put(SELECTED_TOKEN, null);
            }
            else
            {
                mFileList.get(i).remove(SELECTED_TOKEN);
            }
        }

        mSelectFilesInfo = mSelectedFileCount.toString() + " Selected";
        updateFileView();
    }

    public void selectAll()
    {
        if (inSelectFiles)
        {
            for (int i = mFileListView.getCount(); i > 0; )
            {
                if (!mFileListView.isItemChecked(--i))
                {
                    mFileListView.setItemChecked(i, true);
                }
            }

            updateSelectedFileCount();
        }
    }

    public void unselectAll()
    {
        for (int i = mFileListView.getCount(); i > 0; )
        {
            mFileListView.setItemChecked(--i, false);
        }

        updateSelectedFileCount();
    }

    private void doDeleteSelectedFiles()
    {
        for (int i = mFileListView.getCount(); i > 0; )
        {
            if (mFileListView.isItemChecked(--i))
            {
                HashMap<String, Object> itemHashMap = mFileList.get(i);
                remove(itemHashMap.get(TITLE).toString() + itemHashMap.get(TITLE_HIGHLIGHTED).toString(), true);
            }
        }

        reshowFileList();
    }

    public void deleteSelectedFiles()
    {
        if (mSelectedFileCount > 0)
        {
            showAlertDialog(android.R.attr.alertDialogIcon, "Delete", mSelectedFileCount > 1 ? mSelectedFileCount.toString() + " items will be deleted" : "Item will be deleted", true,
                    "Cancel", new OnClickListenerEx(), "Ok", new OnClickListenerEx() { @Override public void onClick() { doDeleteSelectedFiles(); } }, "", null);
        }
    }

    public void toggleFileSelection(boolean selectOn)
    {
        inSelectFiles = selectOn;
        if (inSelectFiles)
        {
            if (mFileListView.getChoiceMode() != ListView.CHOICE_MODE_MULTIPLE_MODAL)
            {
                mFileListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
            }
            mFileListView.setLongClickable(false);
        }
        else
        {
            unselectAll();
            mFileListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

            post(new Runnable() { @Override public void run() { mFileListView.setChoiceMode(ListView.CHOICE_MODE_NONE); } });

            mFileListView.setLongClickable(true);
        }

        updateFileView();
    }

    private class FileListViewAdapter extends SimpleAdapter {

        private float mTitleTextSize;
        private float mInfoTextSize;

        public FileListViewAdapter(float tts, float its)
        {
            super(mMainActivity, mFileList,
                R.layout.file_list_item, new String[]{TITLE, TITLE_HIGHLIGHTED, DESCRIPTION, DESCRIPTION_HIGHLIGHTED, ICON_HIGHLIGHTED},
                new int[]{R.id.file_name, R.id.file_name_highlighted, R.id.file_info, R.id.file_info_highlighted, R.id.file_highlighted});

            mTitleTextSize = tts;
            mInfoTextSize = its;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            View row = super.getView(position, convertView, parent);

            ((android.widget.TextView)row.findViewById(R.id.file_name)).setTextSize(mTitleTextSize);
            ((android.widget.TextView)row.findViewById(R.id.file_info)).setTextSize(mInfoTextSize);
            ((android.widget.TextView)row.findViewById(R.id.file_name_highlighted)).setTextSize(mTitleTextSize);
            ((android.widget.TextView)row.findViewById(R.id.file_info_highlighted)).setTextSize(mInfoTextSize);

            android.widget.ImageView iv = (android.widget.ImageView)row.findViewById(R.id.file_highlighted);
            if (iv != null)
            {
                HashMap<String, Object> hm = mFileList.get(position);
                if (hm != null)
                {
                    //..
                }
            }

            return row;
        }
    }

    private void showFileList(boolean forOpen, boolean beforeOpen, boolean enumFiles)
    {
        fileListVisible = true;
        inOpenFile = forOpen;
        inBeforeOpenFile = beforeOpen;

        setXStuffRect(mFileListTop, mFileListLeft, mFileListRight, mFileListBottom);
        mFilesView.setVisibility(VISIBLE);

        if (enumFiles)
        {
            mFileList.clear();
            enumLocalFiles(!inOpenFile);
            FileListEntryComparator cmp = new FileListEntryComparator();
            java.util.Collections.sort(mFileList, cmp);

            inSelectFiles = false;
        }

        int fileListTopItem = mFileListTopItem;

        FileListViewAdapter adapter = new FileListViewAdapter(mIsTablet ? 20.0f : 16.0f, mIsTablet ? 15.0f : 12.0f);

        mFileListView.setAdapter(adapter);
        mFileListView.setOnScrollListener(scrollListener);
        mFileListView.setOnItemClickListener(itemClickListener);
        mFileListView.setOnItemLongClickListener(itemLongClickListener);

        if (!enumFiles && inSelectFiles) // restore selection
        {
            for (int i = mFileListView.getCount(); i > 0; )
            {
                boolean selected = mFileList.get(--i).containsKey(SELECTED_TOKEN);
                mFileListView.setItemChecked(i, selected);
            }
        }

        mFileListView.setSelection(fileListTopItem);
        toggleFileSelection(inSelectFiles);

        onShowModal();

        if (inNewFileNameInput)
        {
            showNewFileInteface(false);
        }
    }

    private void showFileListForOpen()
    {
        showFileList(true, false, true);
    }

    private void trySaveContentBeforeOpenFile()
    {
        if (saveAs("", true))
        {
            showFileList(true, false, true);
        }
        else
        {
            showFileList(false, false, true); // there is no 'suspeneded' open any more..
        };
    }

    public void onMainMenuOpenFile(boolean saveBeforeOpen, char[] path, int pathSize, int fileSize, int dateInSecs)
    {
        mFileListTopItem = 0;

        if (!saveBeforeOpen)
        {
            showFileListForOpen();
        }
        else
        {
            String message = "Do you want to save the calculation?";

            String fileName = new String(path, 0, pathSize);
            if (!fileName.isEmpty() && (0 != dateInSecs) && (0 != fileSize))
            {
                final int MAX_VISIBLE_FILE_NAME = 48;
                if (fileName.length() > MAX_VISIBLE_FILE_NAME)
                {
                    fileName = fileName.substring(0, MAX_VISIBLE_FILE_NAME) + new String(new char[]{0x2026});
                }

                Date date = new Date(((long)dateInSecs) * 1000);

                message = "Calculation '" + fileName + "'\nnot saved from " + mDateFormat.format(date);
            }

            showAlertDialog(android.R.attr.alertDialogIcon, "Unsaved data", message, false,
                    "Discard", new OnClickListenerEx() { @Override public void onClick() { showFileListForOpen(); } },
                    "Save", new OnClickListenerEx() { @Override public void onClick() { trySaveContentBeforeOpenFile(); } },
                    "Cancel", new OnClickListenerEx() { @Override public void onClick() { showEditor(); } });
        }
    }

    public void onMainMenuSaveFile()
    {
        mFileListTopItem = 0;
        showFileList(false, false, true);
    }

    private String mSharedThumbnailUri;
    private String mSharedCalculationUri;
    private static String mShareFileName = "";
    private static boolean mShareAsImage = true;
    private static boolean mShareAsEditable = true;
    private static final int SHARE_AS_CODE_TAG = 0x0002;
    private static final int SHARE_AS_IMAGE_TAG = 0x0001;

    private android.app.AlertDialog mShareDialog;
    private android.widget.ImageView mThumbnailView;
    private android.widget.CheckBox mShareAsImageCheckBox;
    private android.widget.CheckBox mShareAsEditableCheckBox;

    public void createContextForPaintInMemory(char[] fileName, int fileNameSize, int width, int height)
    {
        mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        mCanvas = new Canvas(mBitmap);

        mShareFileName = new String(fileName, 0, fileNameSize);
    }

    private boolean incheckBoxUpdating;
    private void updateShareDialogButton()
    {
        incheckBoxUpdating = true;
        int thumbnailViewVisibility = INVISIBLE;

        if ((null != mShareDialog) && (null != mShareAsImageCheckBox) && (null != mShareAsEditableCheckBox))
        {
            Button shareButton = mShareDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (null != shareButton)
            {
                mShareAsImageCheckBox.setChecked(mShareAsImage);
                mShareAsEditableCheckBox.setChecked(mShareAsEditable);
                shareButton.setEnabled(mShareAsImage || mShareAsEditable);

                if (mShareAsImage) {
                    thumbnailViewVisibility = VISIBLE;
                }
            }
        }

        if (null != mThumbnailView)
        {
            mThumbnailView.setVisibility(thumbnailViewVisibility);
        }

        incheckBoxUpdating = false;
    }

    private void initCheckBox(android.widget.CheckBox checkBox, String title, String Uri, int tag)
    {
        java.io.File f = new java.io.File(Uri);
        checkBox.setText(title + ", " + fileSizeToString((int)f.length()));
        checkBox.setTag(tag);

        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!incheckBoxUpdating) {
                    if (SHARE_AS_IMAGE_TAG == (int)buttonView.getTag()) {
                        mShareAsImage = isChecked;
                    } else {
                        mShareAsEditable = isChecked;
                    }
                    updateShareDialogButton();
                }
            }
        });
    }

    private void showShareStuff()
    {
        boolean inSharing = false;

        try
        {
            incheckBoxUpdating = false;

            mShareAsImageCheckBox = new android.widget.CheckBox(mMainActivity);
            mShareAsEditableCheckBox = new android.widget.CheckBox(mMainActivity);

            android.widget.TextView mShareAsImageText = new android.widget.TextView(mMainActivity);
            mShareAsImageText.setText("open with any image viewer");

            android.widget.TextView mShareAsEditableText = new android.widget.TextView(mMainActivity);
            mShareAsEditableText.setText("open with Visual Calculator by Windingo");

            float h__ = mShareAsImageCheckBox.getTextSize();

            // output image (thumbnail) to file

            Bitmap orgBitmap = mBitmap;
            Canvas orgCanvas = mCanvas;
            Bitmap thumbnail = null;
            mThumbnailView = null;

            try
            {
                renderInputArea(mCanvas, 0, 0, 0, 0, 0, 0, 0, 0, true);
                thumbnail = mBitmap;

                Display display = mMainActivity.getWindowManager().getDefaultDisplay();
                Rect displayRect = new Rect();
                display.getRectSize(displayRect);

                float thumbnailWidth = (float)thumbnail.getWidth();
                float thumbnailHeight = (float)thumbnail.getHeight();
                float scale = Math.min(1.00f, Math.min(((float)displayRect.width() * 0.75f) / thumbnailWidth, ((float)displayRect.height() * 0.90f - h__ * 16.0f) / thumbnailHeight));
                int width = (int)(thumbnailWidth * scale);
                int height = (int)(thumbnailHeight * scale);

                mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                mCanvas = new Canvas(mBitmap);
                mCanvas.save();
                mCanvas.scale(scale, scale);

                Paint paint = new Paint();
                paint.setAntiAlias(true);

                mCanvas.drawBitmap(thumbnail, 0, 0, paint);
                mCanvas.restore();

                if (scale < 1.0f) // blur to enhance picture quality..
                {
                    if ((null == mBlurRenderScript) && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1))
                    {
                        mBlurRenderScript = android.renderscript.RenderScript.create(mMainActivity);
                    }

                    blurBackground(0.0125f, false);
                    mBlurRenderScript.destroy(); // else -- memory leak :-(
                }

                drawRectangle((0xff << 24) | (0xbf << 16) | (0xbf << 8) | (0xbf), 1.0f, 0, 0, width, height);


                mThumbnailView = new android.widget.ImageView(mMainActivity);
                mThumbnailView.setImageBitmap(mBitmap);
            }
            catch(Throwable t)
            {
                showErrorMessage("Cannot create shared data\n\n" + t.getMessage());
                t.printStackTrace();
            }

            mBitmap = orgBitmap;
            mCanvas = orgCanvas;

            if (null != thumbnail)
            {
                mSharedThumbnailUri = mMainActivity.mLocalSharePlace + "/" + mShareFileName + ".png";
                java.io.FileOutputStream fos = new java.io.FileOutputStream(mSharedThumbnailUri);
                thumbnail.compress(Bitmap.CompressFormat.PNG, 90, fos);
                fos.close();

                // copy calculation to [exports] folder
                mSharedCalculationUri = mMainActivity.mLocalSharePlace + "/" + mShareFileName + ".vcef";
                shareAs(mSharedCalculationUri);

                android.app.AlertDialog.Builder alertDialogBuilder = new android.app.AlertDialog.Builder(mMainActivity);

                LinearLayout layout = new LinearLayout(mMainActivity);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setLayoutParams(params);

                layout.setGravity(android.view.Gravity.CLIP_VERTICAL);
                layout.setPadding(2, 2, 2, 2);

                int horzMargin = 30;
                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                layoutParams.topMargin = horzMargin * 16 / 100; // ~5
                layoutParams.leftMargin = horzMargin;
                layoutParams.rightMargin = horzMargin;
                layoutParams.bottomMargin = horzMargin; // 15?

                if (null != mThumbnailView) {
                    layout.addView(mThumbnailView, layoutParams);

                    LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    llp.topMargin = (int)(h__ * 0.7535f);
                    llp.leftMargin = (int)(h__ * 1.5357f);
                    llp.rightMargin = (int)(h__ * 1.5357f);
                    llp.bottomMargin = (int)(h__ * 0.5357f);
                    llp.gravity = Gravity.LEFT;
                    mThumbnailView.setLayoutParams(llp);
                }

                initCheckBox(mShareAsImageCheckBox, "Image", mSharedThumbnailUri, SHARE_AS_IMAGE_TAG);
                initCheckBox(mShareAsEditableCheckBox,"Editable code", mSharedCalculationUri, SHARE_AS_CODE_TAG);

                layout.addView(mShareAsImageCheckBox, layoutParams);
                layout.addView(mShareAsImageText, layoutParams);
                layout.addView(mShareAsEditableCheckBox, layoutParams);
                layout.addView(mShareAsEditableText, layoutParams);

                {
                    LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    llp.setMargins((int)(h__ * 1.00f), 0, 0, 0); // llp.setMargins(left, top, right, bottom);
                    mShareAsImageCheckBox.setLayoutParams(llp);
                    mShareAsEditableCheckBox.setLayoutParams(llp);

                    LinearLayout.LayoutParams llp1 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    llp1.setMargins((int)(h__ * 3.275f), -(int)(h__ * 0.555f), 0, 0); // llp.setMargins(left, top, right, bottom);
                    mShareAsImageText.setLayoutParams(llp1);
                    mShareAsEditableText.setLayoutParams(llp1);

                    float greyedTextSize = h__ * 0.75f;
                    mShareAsImageText.setTextSize(TypedValue.COMPLEX_UNIT_PX, greyedTextSize);
                    mShareAsEditableText.setTextSize(TypedValue.COMPLEX_UNIT_PX, greyedTextSize);

                    mShareAsImageText.setEnabled(false);
                    mShareAsEditableText.setEnabled(false);
                }

                alertDialogBuilder.setView(layout);
                alertDialogBuilder.setTitle("Share as");
                alertDialogBuilder.setCancelable(false);

                alertDialogBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener()
                {
                    public void onClick(DialogInterface dialog, int whichButton)
                    {
                        mShareFileName = "";
                        showEditor();
                    }
                });

                alertDialogBuilder.setPositiveButton("Share", new DialogInterface.OnClickListener()
                {
                    public void onClick(DialogInterface dialog, int whichButton)
                    {
                        mShareFileName = "";
                        showShareStuff(mShareAsImage, mShareAsEditable);
                    }
                });

                mShareDialog = alertDialogBuilder.create();
                mShareDialog.show();

                updateShareDialogButton();

                inSharing = true;
            }
        }
        catch(Exception e)
        {
            showErrorMessage("Cannot create shared data\n\n" + e.getMessage());
            e.printStackTrace();
        }

        if (!inSharing)
        {
            showEditor();
        }
    }

    private void showShareStuff(boolean shareThumbnail, boolean shareVcef)
    {
        try
        {
            Intent sharingIntent;

            if (shareThumbnail && shareVcef)
            {
                sharingIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);

                ArrayList<Uri> uris = new ArrayList<Uri>();
                uris.add(FileProvider.getUriForFile(mMainActivity, "com.windingo.calculator.fileprovider", new java.io.File(mSharedThumbnailUri)));
                uris.add(FileProvider.getUriForFile(mMainActivity, "com.windingo.calculator.fileprovider", new java.io.File(mSharedCalculationUri)));
                sharingIntent.putExtra(Intent.EXTRA_STREAM, uris);
            }
            else
            {
                sharingIntent = new Intent(Intent.ACTION_SEND);

                sharingIntent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(mMainActivity, "com.windingo.calculator.fileprovider",
                        new java.io.File(shareThumbnail ? mSharedThumbnailUri : mSharedCalculationUri)));
            }

            sharingIntent.setType(shareVcef ? "text/html" : "image/png"); // image/png|jpeg|gif; text/html|plain; application/pdf|vnd.android.package-archive; audio/mpeg4|mpeg|aac|wav|ogg|midi|x-ms-wma; video/mp4|x-msvideo|x-ms-wmv;
            sharingIntent.putExtra(Intent.EXTRA_TITLE, "Begin to calculate properly!");
            sharingIntent.putExtra(Intent.EXTRA_TEXT, "I'm using Visual Calculator by Windingo\nhttp://windingo-ltd.com/vce\n");

            mMainActivity.startActivityForResult(Intent.createChooser(sharingIntent, "Share"), MainActivity.SHARE_ACTIVITY_REQUEST_CODE);
        }
        catch (Exception e)
        {
            showErrorMessage("Cannot share data\n\n" + e.getMessage());
            e.printStackTrace();
            showEditor();
        }
    }

    public void onMainMenuShareFile()
    {
        showShareStuff();
    }

    static boolean skipApplyOptions = false;

    static boolean globalSettingsVisible = false;
    static int mExpressionPrecision = -1;
    static int mTablePrecision = -1;
    static int mFontIndex = 0;
    static int mThemeIndex = 0;
    static boolean mColouredButtons = false;
    static boolean mSoundEffects = true;
    static boolean mFractions = true;

    static boolean customFormatVisible = false;
    static int mFormatContextIndex = 0;
    static int mPrecisionIndex = 0;
    static int mConversionIndex = 0;
    static boolean mVoidFunction = false;
    static boolean mFormatCustomized = false;
    static int mNotationIndex = CUSTOM_FORMAT_NOTATION_DEFAULT;

    private void showOptions(boolean globalSettings)
    {
        skipApplyOptions = true;

        globalSettingsVisible = globalSettings;
        customFormatVisible = !globalSettingsVisible;
        int settingsTop = globalSettingsVisible ? mSettingsTop : mSettingsTop2;

        try
        {
            mSettingsView.setVisibility(VISIBLE);
            setXStuffRect(settingsTop, mSettingsLeft, mSettingsRight, mSettingsBottom);
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams)mSettingsView.getLayoutParams();
            lp.setMargins(mSettingsLeft, settingsTop, 0, 0);
            mSettingsView.setLayoutParams(lp);
        }
        catch (Exception e)
        {
            mSettingsView.setVisibility(INVISIBLE);
            globalSettingsVisible = false;
            customFormatVisible = false;
            return;
        }

        mSettingsPrecisionTitleTextView.setVisibility(VISIBLE);
        mSettingsPrecisionExprLayout.setVisibility(VISIBLE);
        mSettingsPrecisionTableLayout.setVisibility(VISIBLE);
        mSettingsFunctionLayout.setVisibility(GONE);
        mSettingsNotationLayout.setVisibility(GONE);
        mSettingsConvertLayout.setVisibility(GONE);
        mSettingsResetLayout.setVisibility(GONE);

        mSettingsPrecisionExprSpinner.setOnItemSelectedListener(null);
        mSettingsPrecisionTableSpinner.setOnItemSelectedListener(null);
        mSettingsFunctionSpinner.setOnItemSelectedListener(null);
        mSettingsNotationSpinner.setOnItemSelectedListener(null);
        mSettingsConvertSpinner.setOnItemSelectedListener(null);
        mSettingsFontSpinner.setOnItemSelectedListener(null);
        mSettingsThemeSpinner.setOnItemSelectedListener(null);

        if (globalSettingsVisible)
        {
            mSettingsToolbar.setTitle("Settings");

            mSettingsThemeLayout.setVisibility(VISIBLE);
            mSettingsFontLayout.setVisibility(VISIBLE);
            mSwitchColoredButtons.setVisibility(VISIBLE);
            mSwitchSoundEffects.setVisibility(VISIBLE);
            mSwitchFractions.setVisibility(VISIBLE);

            mSettingsPrecisionExprSpinner.setSelection(mExpressionPrecision, false);
            mSettingsPrecisionTableSpinner.setSelection(mTablePrecision, false);
            mSettingsThemeSpinner.setSelection(mThemeIndex, false);
            mSettingsFontSpinner.setSelection(mFontIndex, false);

            mSettingsPrecisionExprSpinner.setOnItemSelectedListener(this);
            mSettingsPrecisionTableSpinner.setOnItemSelectedListener(this);
            mSettingsThemeSpinner.setOnItemSelectedListener(this);
            mSettingsFontSpinner.setOnItemSelectedListener(this);

            mSwitchColoredButtons.setChecked(mColouredButtons);
            mSwitchSoundEffects.setChecked(mSoundEffects);
            mSwitchFractions.setChecked(mFractions);
        }
        else
        {
            mSettingsToolbar.setTitle("Custom format");

            switch (mFormatContextIndex)
            {
                case 1: // function
                    mSettingsFunctionLayout.setVisibility(VISIBLE);
                    mSettingsFunctionSpinner.setSelection(mVoidFunction ? 1 : 0, false);
                    mSettingsFunctionSpinner.setOnItemSelectedListener(this);

                    mSettingsPrecisionTitleTextView.setVisibility(GONE);
                    mSettingsPrecisionTableLayout.setVisibility(GONE);
                    mSettingsPrecisionExprLayout.setVisibility(GONE);
                    break;

                case 2: // table
                    mSettingsPrecisionTableSpinner.setSelection(mPrecisionIndex, false);
                    mSettingsPrecisionTableSpinner.setOnItemSelectedListener(this);
                    mSettingsPrecisionExprLayout.setVisibility(GONE);
                    break;

                default: // expression
                    mSettingsPrecisionExprSpinner.setSelection(mPrecisionIndex, false);
                    mSettingsNotationSpinner.setSelection(mNotationIndex, false);
                    mSettingsConvertSpinner.setSelection(mConversionIndex, false);
                    mSettingsPrecisionExprSpinner.setOnItemSelectedListener(this);
                    mSettingsNotationSpinner.setOnItemSelectedListener(this);
                    mSettingsConvertSpinner.setOnItemSelectedListener(this);
                    mSettingsNotationLayout.setVisibility(VISIBLE);
                    mSettingsConvertLayout.setVisibility(VISIBLE);
                    mSettingsPrecisionTableLayout.setVisibility(GONE);
                    break;
            }

            mSettingsResetLayout.setVisibility(mFormatCustomized ? VISIBLE : GONE);

            mSettingsFontLayout.setVisibility(GONE);
            mSettingsThemeLayout.setVisibility(GONE);
            mSwitchColoredButtons.setVisibility(GONE);
            mSwitchSoundEffects.setVisibility(GONE);
            mSwitchFractions.setVisibility(GONE);
        }

        mSettingsToolbarLight.setTitle(mSettingsToolbar.getTitle());

        skipApplyOptions = false;
    }

    public void showFormatDialog(int contextIndex, int precisionIndex, int conversionIndex, boolean isCustomized, int notationIndex)
    {
        mFormatContextIndex = contextIndex;
        mPrecisionIndex = precisionIndex + (2 == mFormatContextIndex ? 0 : 1);
        mConversionIndex = conversionIndex;
        mVoidFunction = conversionIndex < 0;
        mFormatCustomized = isCustomized;
        mNotationIndex = notationIndex;

        showOptions(false);
    }

    public void onMainMenuGlobalSettings(int precisionIndex, int tablePrecisionIndex, int fontIndex, int themeIndex, boolean fractionsOn, boolean soundOn, boolean colouredButtonsOn)
    {
        mFontIndex = fontIndex;
        mThemeIndex = themeIndex;
        mExpressionPrecision = precisionIndex + 1;
        mTablePrecision = tablePrecisionIndex;
        mColouredButtons = colouredButtonsOn;
        mSoundEffects = soundOn;
        mFractions = fractionsOn;

        mMainActivity.applyTheme(0 == mThemeIndex ? 1 : 0);
        showOptions(true);
    }

    private void applyOptions()
    {
        if (!skipApplyOptions)
        {
            skipApplyOptions = true;

            if (globalSettingsVisible)
            {
                resetVceBannerStuff();

                applyGlobalSettings(
                        mSettingsPrecisionExprSpinner.getSelectedItemPosition(),
                        mSettingsPrecisionTableSpinner.getSelectedItemPosition(),
                        mSettingsFontSpinner.getSelectedItemPosition(),
                        mSettingsThemeSpinner.getSelectedItemPosition(),
                        mSwitchFractions.isChecked(),
                        mSwitchSoundEffects.isChecked(),
                        mSwitchColoredButtons.isChecked());
            }
            else if (customFormatVisible)
            {
                mPrecisionIndex = 2 == mFormatContextIndex ? mSettingsPrecisionTableSpinner.getSelectedItemPosition() : mSettingsPrecisionExprSpinner.getSelectedItemPosition();
                mConversionIndex = 1 == mFormatContextIndex ? mSettingsFunctionSpinner.getSelectedItemPosition() : mSettingsConvertSpinner.getSelectedItemPosition();
                mNotationIndex = mSettingsNotationSpinner.getSelectedItemPosition();

                applyCustomFormat(mFormatContextIndex, mPrecisionIndex, mConversionIndex, true, mNotationIndex);
            }

            skipApplyOptions = false;
        }
    }

    public native boolean inTutorial();
    public void gotoFullscreenOnTutorial()
    {
        if (!mMainActivity.mInFullscreen && !mMainActivity.mTurnOffFullscreen && inTutorial())
        {
            mMainActivity.mTurnOffFullscreen = true;
            mMainActivity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    public void onMainMenuShowTutorial()
    {
        resetTutorialStuff();
        gotoFullscreenOnTutorial();
    }

    public void onMainMenuBuyFullVersion()
    {
        mMainActivity.onBuyFullVersion();
    }

    public void onTutorialLink()
    {
        Intent browse = new Intent(Intent.ACTION_VIEW , Uri.parse("http://www.Windingo-ltd.com"));
        mMainActivity.startActivity( browse );
    }

    public final int BEEP_FAILURE   = 1;
    public final int BEEP_CLICK     = 2;

    public void beep(int beepId)
    {
        try
        {
            if (BEEP_CLICK == beepId)
            {
                mTockPlayer.start();
            }
            else if (beepId > 0)
            {
                mBeepPlayer.start();
            }
        }
        catch (Exception e)
        {
        }
    }

    public void showErrorMessage(char[] message, int count)
    {
        showErrorMessage(new String(message, 0, count));
    }

    public void showMessage(String title_, String message_)
    {
        showAlertDialog(android.R.attr.alertDialogIcon, title_, message_, true, "Close", new OnClickListenerEx(), "", null, "", null);
    }

    public void showErrorMessage(String title_)
    {
        String message_ = null;

        int messageTokenIndex = title_.indexOf("\n\n");
        if (messageTokenIndex >= 0)
        {
            message_ = title_.substring(messageTokenIndex + 2);
            title_ = title_.substring(0, messageTokenIndex);
        }

        if (title_.length() > 33)
        {
            message_ = title_ + "\n\n" + (null == message_ ? "" : message_);
            title_ = "Failure";
        }

        showAlertDialog(android.R.attr.alertDialogIcon, title_, message_, true, "Close", new OnClickListenerEx(), "", null, "", null);
    }

    private final int BUY_FULL_VERSION_CLOSE            = 0;
    private final int BUY_FULL_VERSION_GOTO_STORE       = 1;
    private final int BUY_FULL_VERSION_DONT_SHOW_AGAIN  = 2;
    public void showBuyFullVersionDialog()
    {
        showAlertDialog(
                android.R.attr.alertDialogIcon,

                "Too many expressions!",
                "The free version evaluates no more than five expressions,\n" +
                "have restricted open/save features and shows ads.\n\nWhen you get the Premium Pack you'll forget about the restrictions.\n",

                false,

                "Don't show again", new OnClickListenerEx() { @Override public void onClick() { onBuyFullVersionClose(BUY_FULL_VERSION_DONT_SHOW_AGAIN); }},
                "Upgrade"          , new OnClickListenerEx() { @Override public void onClick() { mMainActivity.onBuyFullVersion(); }},
                "Close"            , new OnClickListenerEx() { @Override public void onClick() { onBuyFullVersionClose(BUY_FULL_VERSION_CLOSE); }}
        );
    }

    public void showSettingsPromptDialog(String title, String prompt)
    {
        showAlertDialog(android.R.attr.alertDialogIcon, title, prompt, false,

                "Close", new OnClickListenerEx() { @Override public void onClick() {}},
                "Settings", new OnClickListenerEx() {
                    @Override
                    public void onClick() {
                        Intent intent = new Intent();
                        intent.setAction(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", mMainActivity.getPackageName(), null);
                        intent.setData(uri);
                        mMainActivity.startActivity(intent);
                    }
                },
                "", null
        );
    }

    public int getKeyboardHeight()
    {
        return mKeyboardHeight;
    }

    public void showScreenKeyboard(boolean visible)
    {
        if (0 == mInputAreaHeight)
        {
            mInputAreaHeight = getHeight();
        }

        mScreenKeyboardIsVisible = visible;
        mMainActivity.showKeyboard(visible);
    }

    public void invalidateInputArea(int left, int top, int width, int height, boolean forButton)
    {
        if ((width < 0) || (height < 0) || (mRectForInvalidate.right > mRectForInvalidate.left))
        {
            if (mRectForInvalidate.right >= 0)
            {
                mRectForInvalidate.top = mRectForInvalidate.left = 0;
                mRectForInvalidate.right = mRectForInvalidate.bottom = -1;

                invalidate();
            }
        }
        else
        {
            mRectForInvalidate.top = top;
            mRectForInvalidate.left = left;
            mRectForInvalidate.right = left + width;
            mRectForInvalidate.bottom = top + height;

            invalidate();
        }
    }

    private final String VCEF_DATA_FORMAT_ID = ".vcef1.0";
    public void copyData(char[] data, int count, boolean asText)
    {
        if (count > 0)
        {
            try
            {
                android.content.ClipData clip = android.content.ClipData.newPlainText(VCEF_DATA_FORMAT_ID, new String(data, 0, count));
                mClipboard.setPrimaryClip(clip);
            }
            catch (Exception e)
            {
                onCanPasteChanged(false);
            }
        }
    }

    public void pasteData(boolean fromUiButton)
    {
        try
        {
            if (mClipboard.hasPrimaryClip())
            {
                if (mClipboard.getPrimaryClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN));
                {
                    onPaste(mClipboard.getPrimaryClip().getItemAt(0).getText().toString() , fromUiButton);
                }
            }
        }
        catch (Exception e)
        {
            onCanPasteChanged(false);
        }
    }

    public void clearClipboard()
    {
    }

    public int getCurrentDate()
    {
        Calendar cal = Calendar.getInstance();
        return (cal.get(Calendar.YEAR) << 16) | (cal.get(Calendar.MONTH) << 8) | cal.get(Calendar.DAY_OF_MONTH);
    }


    // ad

    private void showAdBanner(int left, int top, int width, int height)
    {
        //..
    }

    private void hideAdBanner()
    {
        //..
    }

}
