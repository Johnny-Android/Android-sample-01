/*
 * android to calc engine gate (JNI), draw primitives, file i/o etc.
 *
 */

#include "jni.h"

#include <list>
#include <string>
#include <vector>
#include <memory>

#include "sys/types.h"
#include "sys/stat.h"
#include "dirent.h"


JNIEnv *jnienv_ = nullptr;

jobject  inputAreaViewObj_ = nullptr;
void freeInputAreaViewObj()
{
    if ((nullptr != jnienv_) && (nullptr != inputAreaViewObj_))
    {
        jnienv_->DeleteGlobalRef(inputAreaViewObj_);
        inputAreaViewObj_ = nullptr;
    }
}

jmethodID releaseResources_;

jmethodID createFont_;
jmethodID createFont_i_;
jmethodID getFontHeight_;
jmethodID getCharWidth_;
jmethodID getStringWidth_;

jmethodID setClipRect_;
jmethodID setSmoothingMode_;

jmethodID applyAndReleaseDrawingMatrix_;
jmethodID restorePreviousDrawingMatrix_;

jmethodID fillRectangle_i_;
jmethodID fillRectangle_f_;
jmethodID drawRectangle_;
jmethodID fillEllipse_;
jmethodID drawEllipse_;
jmethodID drawLine_f_;
jmethodID drawLine_i_;
jmethodID drawLineWithFlatCaps_i_;
jmethodID drawLines_3_;
jmethodID drawLines_5_;
jmethodID drawString_f_;
jmethodID drawString_i_;
jmethodID drawString_r_;
jmethodID drawStringAtCenter_;
jmethodID drawStringAtCenterHorz_;
jmethodID drawChar_;
jmethodID createGraphicsPath_;
jmethodID addLine_;
jmethodID addArc_r_;
jmethodID addArc_a_;
jmethodID addBezier_;
jmethodID fillPath_;
jmethodID drawPath_;
jmethodID drawBitmap_;
jmethodID setAlpha_;
jmethodID blurPopupBackground_;
jmethodID createContextForPaintInMemory_;
jmethodID drawTutorialPageBitmap_;
jmethodID drawScreenshot_;
jmethodID drawVceBannerBackground_;
jmethodID showAdBanner_;
jmethodID hideAdBanner_;

jmethodID getCurrentDate_;

jmethodID showMainMenu_;
jmethodID showFormatDialog_;
jmethodID onClosePopup_;
jmethodID onMainMenuOpenFile_;
jmethodID onMainMenuSaveFile_;
jmethodID onMainMenuShareFile_;
jmethodID onMainMenuGlobalSettings_;
jmethodID onMainMenuShowTutorial_;
jmethodID onMainMenuBuyFullVersion_;
jmethodID onTutorialLink_;
jmethodID beep_;
jmethodID placeAdBar_;
jmethodID showErrorMessage_;
jmethodID showBuyFullVersionDialog_;
jmethodID getKeyboardHeight_;
jmethodID showScreenKeyboard_;
jmethodID invalidateInputArea_;
jmethodID updateInputContext_;
jmethodID copyData_;
jmethodID pasteData_;
jmethodID clearClipboard_;
jmethodID addFileToList_;

int charArraySize = 0;
int charArrayCount = 0;
std::vector<jchar> jchars;
jcharArray charArray = nullptr;
void setCharArray(const std::string &s)
{
    int charArrayCount_ = (int)s.size();

    if (charArraySize < charArrayCount_)
    {
        charArraySize = charArrayCount_ * 11 / 10;
        jchars.resize(charArraySize);
        charArray = jnienv_->NewCharArray(charArraySize);
    }

    charArrayCount = charArrayCount_;
    for (int i = 0; i < charArrayCount; ++i)
    {
        jchars[i] = s[i];
    }

    jnienv_->SetCharArrayRegion(charArray, 0, charArrayCount, &jchars[0]);
}

void jstringToString(jstring &js, std::string &s)
{
    s.clear();

    const jchar* js_ = jnienv_->GetStringChars(js, JNI_FALSE);
    int localStorageSize = jnienv_->GetStringLength(js);
    for (int i = 0; i < localStorageSize; ++i)
    {
        s += js_[i];
    }

    jnienv_->ReleaseStringChars(js, js_);
}

class CallbackStuff
{
public: CallbackStuff()
    {
        charArray = jnienv_->NewCharArray(charArraySize);
    }
public: ~CallbackStuff()
    {
    }
};


extern "C"
JNIEXPORT void JNICALL Java_com_windingo_vc_MainActivity_initUiGate(JNIEnv * env, jobject  obj, jobject inputAreaView, int left, int top, int width, int height, float dpi, bool deviceIsTablet, jstring localStorage, jboolean inFullscreen)
{
    // cache JNI stuff (object & methods)

    jnienv_ =  env;

    freeInputAreaViewObj();
    inputAreaViewObj_ = jnienv_->NewGlobalRef(inputAreaView);

    if (0 == charArraySize)
    {
        charArraySize = 256;
        jchars.resize(charArraySize);
    }

    jclass clazz = jnienv_->FindClass("com/windingo/vc/InputAreaView");

    releaseResources_ = jnienv_->GetMethodID(clazz, "releaseResources", "()V");
    createFont_ = jnienv_->GetMethodID(clazz, "createFont", "([CIFZZI)I");
    createFont_i_ = jnienv_->GetMethodID(clazz, "createFont", "(IFZZI)I");
    getCharWidth_ = jnienv_->GetMethodID(clazz, "getCharWidth", "(CI)F");
    getFontHeight_ = jnienv_->GetMethodID(clazz, "getFontHeight", "(I)F");
    getStringWidth_ = jnienv_->GetMethodID(clazz, "getStringWidth", "([CII)F");
    setClipRect_ = jnienv_->GetMethodID(clazz, "setClipRect", "(IIII)V");
    setSmoothingMode_ = jnienv_->GetMethodID(clazz, "setSmoothingMode", "(Z)V");
    applyAndReleaseDrawingMatrix_ = jnienv_->GetMethodID(clazz, "applyAndReleaseDrawingMatrix", "(FFFFF)V");
    restorePreviousDrawingMatrix_ = jnienv_->GetMethodID(clazz, "restorePreviousDrawingMatrix", "()V");
    fillRectangle_i_ = jnienv_->GetMethodID(clazz, "fillRectangle", "(IIIII)V");
    fillRectangle_f_ = jnienv_->GetMethodID(clazz, "fillRectangle", "(IFFFF)V");
    drawRectangle_ = jnienv_->GetMethodID(clazz, "drawRectangle", "(IFFFFF)V");
    fillEllipse_ = jnienv_->GetMethodID(clazz, "fillEllipse", "(IFFFF)V");
    drawEllipse_ = jnienv_->GetMethodID(clazz, "drawEllipse", "(IFFFFF)V");
    drawLine_f_ = jnienv_->GetMethodID(clazz, "drawLine", "(IFFFFF)V");
    drawLine_i_ = jnienv_->GetMethodID(clazz, "drawLine", "(IFIIII)V");
    drawLineWithFlatCaps_i_ = jnienv_->GetMethodID(clazz, "drawLineWithFlatCaps", "(IFIIII)V");
    drawLines_3_ = jnienv_->GetMethodID(clazz, "drawLines", "(IFIIIIII)V");
    drawLines_5_ = jnienv_->GetMethodID(clazz, "drawLines", "(IFIIIIIIIIII)V");
    drawString_f_ = jnienv_->GetMethodID(clazz, "drawString", "([CIIIFF)V");
    drawString_i_ = jnienv_->GetMethodID(clazz, "drawString", "([CIIIII)V");
    drawString_r_ = jnienv_->GetMethodID(clazz, "drawString", "([CIIIIIII)V");
    drawStringAtCenter_ = jnienv_->GetMethodID(clazz, "drawStringAtCenter", "([CIIIIIII)V");
    drawStringAtCenterHorz_ = jnienv_->GetMethodID(clazz, "drawStringAtCenterHorz", "([CIIIFFF)V");
    drawChar_ = jnienv_->GetMethodID(clazz, "drawChar", "(CIIFF)V");
    createGraphicsPath_ = jnienv_->GetMethodID(clazz, "createGraphicsPath", "()Landroid/graphics/Path;");
    addLine_ = jnienv_->GetMethodID(clazz, "addLine", "(Landroid/graphics/Path;FFFF)V");
    addArc_r_ = jnienv_->GetMethodID(clazz, "addArc", "(Landroid/graphics/Path;FFFFF)V");
    addArc_a_ = jnienv_->GetMethodID(clazz, "addArc", "(Landroid/graphics/Path;FFFFFF)V");
    addBezier_ = jnienv_->GetMethodID(clazz, "addBezier", "(Landroid/graphics/Path;FFFFFFFF)V");
    fillPath_ = jnienv_->GetMethodID(clazz, "fillPath", "(Landroid/graphics/Path;I)V");
    drawPath_ = jnienv_->GetMethodID(clazz, "drawPath", "(Landroid/graphics/Path;IF)V");
    drawBitmap_ = jnienv_->GetMethodID(clazz, "drawBitmap", "(III)V");
    setAlpha_ = jnienv_->GetMethodID(clazz, "setAlpha", "(F)V");
    blurPopupBackground_ = jnienv_->GetMethodID(clazz, "blurPopupBackground", "()Z");
    createContextForPaintInMemory_ = jnienv_->GetMethodID(clazz, "createContextForPaintInMemory", "([CIII)V");
    drawTutorialPageBitmap_ = jnienv_->GetMethodID(clazz, "drawTutorialPageBitmap", "(IIIII)V");
    drawScreenshot_ = jnienv_->GetMethodID(clazz, "drawScreenshot", "(FFIZI)V");
    drawVceBannerBackground_ = jnienv_->GetMethodID(clazz, "drawVceBannerBackground", "(IIIIIZ)V");
    showAdBanner_ = jnienv_->GetMethodID(clazz, "showAdBanner", "(IIII)V");
    hideAdBanner_ = jnienv_->GetMethodID(clazz, "hideAdBanner", "()V");

    getCurrentDate_ = jnienv_->GetMethodID(clazz, "getCurrentDate", "()I");

    showMainMenu_ = jnienv_->GetMethodID(clazz, "showMainMenu", "(Z)V");
    showFormatDialog_ = jnienv_->GetMethodID(clazz, "showFormatDialog", "(IIIZI)V");
    onClosePopup_ = jnienv_->GetMethodID(clazz, "onClosePopup", "()V");
    onMainMenuOpenFile_ = jnienv_->GetMethodID(clazz, "onMainMenuOpenFile", "(Z[CIII)V");
    onMainMenuSaveFile_ = jnienv_->GetMethodID(clazz, "onMainMenuSaveFile", "()V");
    onMainMenuShareFile_ = jnienv_->GetMethodID(clazz, "onMainMenuShareFile", "()V");
    onMainMenuGlobalSettings_ = jnienv_->GetMethodID(clazz, "onMainMenuGlobalSettings", "(IIIIZZZ)V");
    onMainMenuShowTutorial_ = jnienv_->GetMethodID(clazz, "onMainMenuShowTutorial", "()V");
    onMainMenuBuyFullVersion_ = jnienv_->GetMethodID(clazz, "onMainMenuBuyFullVersion", "()V");
    onTutorialLink_ = jnienv_->GetMethodID(clazz, "onTutorialLink", "()V");
    beep_ = jnienv_->GetMethodID(clazz, "beep", "(I)V");
    showErrorMessage_ = jnienv_->GetMethodID(clazz, "showErrorMessage", "([CI)V");
    showBuyFullVersionDialog_ = jnienv_->GetMethodID(clazz, "showBuyFullVersionDialog", "()V");
    getKeyboardHeight_ = jnienv_->GetMethodID(clazz, "getKeyboardHeight", "()I");
    showScreenKeyboard_ = jnienv_->GetMethodID(clazz, "showScreenKeyboard", "(Z)V");
    invalidateInputArea_ = jnienv_->GetMethodID(clazz, "invalidateInputArea", "(IIIIZ)V");
    updateInputContext_ = jnienv_->GetMethodID(clazz, "updateInputContext", "([CIII)V");
    copyData_ = jnienv_->GetMethodID(clazz, "copyData", "([CIZ)V");
    pasteData_ = jnienv_->GetMethodID(clazz, "pasteData", "(Z)V");
    clearClipboard_ = jnienv_->GetMethodID(clazz, "clearClipboard", "()V");
    addFileToList_ = jnienv_->GetMethodID(clazz, "addFileToList", "([CIIIZ)V");

    jnienv_->DeleteLocalRef(clazz);

    // init..

    CallbackStuff cbs;
}

extern "C"
JNIEXPORT void JNICALL Java_com_windingo_vc_MainActivity_onOpenFile(JNIEnv * env, jobject  obj, jstring path)
{
}

extern "C"
JNIEXPORT bool JNICALL Java_com_windingo_vc_MainActivity_getVersionTag(JNIEnv * env, jobject  obj)
{
    return true;
}

extern "C"
JNIEXPORT void JNICALL Java_com_windingo_vc_MainActivity_setVersionTag(JNIEnv * env, jobject  obj, jboolean isTrial)
{
}

extern "C"
JNIEXPORT void JNICALL Java_com_windingo_vc_MainActivity_freeUiGate(JNIEnv * env, jobject  obj)
{
    freeInputAreaViewObj();
}

extern "C"
JNIEXPORT void JNICALL Java_com_windingo_vc_MainActivity_saveState(JNIEnv * env, jobject  obj)
{
}

extern "C"
JNIEXPORT bool JNICALL Java_com_windingo_vc_MainActivity_onBackRequested(JNIEnv * env, jobject  obj)
{
    return false;
}

extern "C"
JNIEXPORT void JNICALL Java_com_windingo_vc_MainActivity_onTouch(JNIEnv * env, jobject  obj, int event, int x, int y, float scale)
{
}

extern "C"
JNIEXPORT void JNICALL Java_com_windingo_vc_MainActivity_setSeparators(JNIEnv * env, jobject  obj, jchar decimalSeparator, jchar groupingSeparator)
{
}

extern "C"
JNIEXPORT int JNICALL Java_com_windingo_vc_MainActivity_getUiTheme(JNIEnv * env, jobject  obj)
{
    return 0;
}

extern "C"
JNIEXPORT int JNICALL Java_com_windingo_vc_MainActivity_getUiBorderWidth(JNIEnv * env, jobject  obj)
{
    return 0;
}

extern "C"
JNIEXPORT int JNICALL Java_com_windingo_vc_MainActivity_getUiSeparatorWidth(JNIEnv * env, jobject  obj)
{
    return 0;
}

extern "C"
JNIEXPORT void JNICALL Java_com_windingo_vc_InputAreaView_onShowBannerSuccess(JNIEnv * env, jobject  obj, int bannerWidth, int bannerHeight)
{
}

extern "C"
JNIEXPORT void JNICALL Java_com_windingo_vc_InputAreaView_onShowBannerFailure(JNIEnv * env, jobject  obj, int errorCode)
{
}

extern "C"
JNIEXPORT bool JNICALL Java_com_windingo_vc_InputAreaView_renderInputArea(JNIEnv * env, jobject  obj, jobject canvas, int left, int top, int width, int height, int clipRectLeft, int clipRectTop, int clipRectWidth, int clipRectHeight, bool inMemory)
{
    return true;
}

extern "C"
JNIEXPORT void JNICALL Java_com_windingo_vc_InputAreaView_animateTutorial(JNIEnv * env, jobject  obj)
{
}

extern "C"
JNIEXPORT bool JNICALL Java_com_windingo_vc_InputAreaView_hideButtonPanel(JNIEnv * env, jobject  obj)
{
    return false;
}

extern "C"
JNIEXPORT void JNICALL Java_com_windingo_vc_InputAreaView_onCursorTimerTick(JNIEnv * env, jobject  obj)
{
}

extern "C"
JNIEXPORT void JNICALL Java_com_windingo_vc_InputAreaView_updateKeyboardHeight(JNIEnv * env, jobject  obj, int newKeyboardHeight)
{
}

extern "C"
JNIEXPORT void JNICALL Java_com_windingo_vc_InputAreaView_onInput(JNIEnv * env, jobject  obj, jstring oldText, jstring newText)
{
}

extern "C"
JNIEXPORT void JNICALL Java_com_windingo_vc_InputAreaView_onCharInput(JNIEnv * env, jobject  obj, jchar input)
{
}

extern "C"
JNIEXPORT void JNICALL Java_com_windingo_vc_InputAreaView_onDelete(JNIEnv * env, jobject  obj, bool backspace)
{
}

extern "C"
JNIEXPORT void JNICALL Java_com_windingo_vc_InputAreaView_onCursor(JNIEnv * env, jobject  obj, int moving, bool select)
{
}

extern "C"
JNIEXPORT void JNICALL Java_com_windingo_vc_InputAreaView_onCanPasteChanged(JNIEnv * env, jobject  obj, bool canPaste)
{
}

extern "C"
JNIEXPORT void JNICALL Java_com_windingo_vc_InputAreaView_onShortcut(JNIEnv * env, jobject  obj, int shortcut)
{
}

extern "C"
JNIEXPORT void JNICALL Java_com_windingo_vc_InputAreaView_onPaste(JNIEnv * env, jobject  obj, jstring text, bool fromUiButton)
{
}

extern "C"
JNIEXPORT void JNICALL Java_com_windingo_vc_InputAreaView_onBuyFullVersionClose(JNIEnv * env, jobject  obj, int option)
{
}

extern "C"
JNIEXPORT void JNICALL Java_com_windingo_vc_InputAreaView_onShowModal(JNIEnv * env, jobject  obj)
{
}

extern "C"
JNIEXPORT void JNICALL Java_com_windingo_vc_InputAreaView_enumLocalFiles(JNIEnv * env, jobject  obj, bool excludeLocalSamples)
{
}

extern "C"
JNIEXPORT bool JNICALL Java_com_windingo_vc_InputAreaView_shareAs(JNIEnv * env, jobject  obj, jstring fileName)
{
    return true;
}

extern "C"
JNIEXPORT bool JNICALL Java_com_windingo_vc_InputAreaView_saveAs(JNIEnv * env, jobject  obj, jstring fileName, bool inLocalStorage)
{
    return true;
}

extern "C"
JNIEXPORT bool JNICALL Java_com_windingo_vc_InputAreaView_open(JNIEnv * env, jobject  obj, jstring fileName, bool inLocalStorage)
{
    return true;
}

extern "C"
JNIEXPORT bool JNICALL Java_com_windingo_vc_InputAreaView_remove(JNIEnv * env, jobject  obj, jstring fileName, bool inLocalStorage)
{
    return true;
}

extern "C"
JNIEXPORT void JNICALL Java_com_windingo_vc_InputAreaView_showEditor(JNIEnv * env, jobject  obj)
{
}

extern "C"
JNIEXPORT jboolean JNICALL Java_com_windingo_vc_InputAreaView_blurBitmap(JNIEnv * env, jobject  obj, jobject bitmap)
{
    return false; // -- no any blur implemented
}

extern "C"
JNIEXPORT jboolean JNICALL Java_com_windingo_vc_InputAreaView_inTutorial(JNIEnv * env, jobject  obj)
{
    return false;
}

extern "C"
JNIEXPORT void JNICALL Java_com_windingo_vc_InputAreaView_captureTutorialPage(JNIEnv * env, jobject  obj, int pageWidth, int pageHeight, int pageId)
{
}

extern "C"
JNIEXPORT void JNICALL Java_com_windingo_vc_InputAreaView_prepareVceBannerBackground(JNIEnv * env, jobject  obj, int brush, int width_i, int height_i, bool drawRoundTopRight)
{
}

extern "C"
JNIEXPORT void JNICALL Java_com_windingo_vc_InputAreaView_applyCustomFormat(JNIEnv * env, jobject  obj, int contextIndex, int precisionIndex, int conversionIndex, bool isCustomized, int notation)
{
}

extern "C"
JNIEXPORT void JNICALL Java_com_windingo_vc_InputAreaView_applyGlobalSettings(JNIEnv * env, jobject  obj, int precisionIndex, int tablePrecisionIndex, int fontIndex, int themeIndex, bool fractionsOn, bool soundOn, bool colouredButtonsOn)
{
}

extern "C"
JNIEXPORT jint JNICALL Java_com_windingo_vc_InputAreaView_getPrevTutorialPageId(JNIEnv * env, jobject  obj, int pageId)
{
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL Java_com_windingo_vc_InputAreaView_getNextTutorialPageId(JNIEnv * env, jobject  obj, int pageId)
{
    return 0;
}

extern "C"
JNIEXPORT void JNICALL Java_com_windingo_vc_InputAreaView_setXStuffRect(JNIEnv * env, jobject  obj, int top, int left, int right, int bottom)
{
}
