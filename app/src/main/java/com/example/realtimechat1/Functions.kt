package com.example.realtimechat1

import android.content.Context
import android.widget.Toast

/**
 * Created by keybowNew on 2017/11/20.
 */
fun makeToast(content: Context, message: String){
    Toast.makeText(content, message, Toast.LENGTH_SHORT).show()
}