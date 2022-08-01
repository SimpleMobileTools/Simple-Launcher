package com.simplemobiletools.launcher.extensions

import android.content.Context
import com.simplemobiletools.launcher.helpers.Config

val Context.config: Config get() = Config.newInstance(applicationContext)
