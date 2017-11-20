package com.inucreative.sednremocon;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Created by Jskim on 2016-06-29.
 */
public class KeyboardDialog extends Dialog {
    Context mContext;
    EditText etDummy;
    String mDefaultText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.keyboard_dialog);

        etDummy = (EditText) findViewById(R.id.etKeyboard);
        etDummy.setText(mDefaultText);
        etDummy.requestFocus();

        getWindow().setBackgroundDrawableResource(R.drawable.transparent_background);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

        etDummy.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                LogUtil.d("beforeTextChanged : " + s);
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                LogUtil.d("onTextChanged : " + s);
            }

            @Override
            public void afterTextChanged(Editable s) {
                LogUtil.d("afterTextChanged : " + s);
                ((MainActivity)mContext).sendStringtoServer(s.toString());
            }
        });

        etDummy.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                LogUtil.d("onEditorAction : " + actionId);
                if(actionId == EditorInfo.IME_ACTION_DONE) {
                    dismiss();
                }
                return false;
            }
        });
    }

    public KeyboardDialog(Context context, String defaultText) {
        super(context);
        mContext = context;
        mDefaultText = defaultText;
    }


}