package com.stelliberty.android.ui.component.blur

import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode

// 毛玻璃混色参数。名字沿用材质本身的叫法，与深浅色模式无关——两组都带 Light 后缀，
// 但实际是 Pured_Regular_Light 用于浅色、Overlay_Thin_Light 用于深色，别照名字反推。
internal object ColorBlendToken {

    val Pured_Regular_Light = listOf(
        BlendColorEntry(Color(0x340034F9), BlurBlendMode.Overlay),
        BlendColorEntry(Color(0xB3FFFFFF.toInt()), BlurBlendMode.HardLight),
    )

    val Overlay_Thin_Light = listOf(
        BlendColorEntry(Color(0x4DA9A9A9), BlurBlendMode.Luminosity),
        BlendColorEntry(Color(0x1A9C9C9C), BlurBlendMode.PlusDarker),
    )

    val LogoLight = listOf(
        BlendColorEntry(Color(0xcc4a4a4a.toInt()), BlurBlendMode.ColorBurn),
        BlendColorEntry(Color(0xff4f4f4f.toInt()), BlurBlendMode.LinearLight),
        BlendColorEntry(Color(0xff1af200.toInt()), BlurBlendMode.Lab),
    )

    val LogoDark = listOf(
        BlendColorEntry(Color(0xe6a1a1a1.toInt()), BlurBlendMode.ColorDodge),
        BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
        BlendColorEntry(Color(0xff1af500.toInt()), BlurBlendMode.Lab),
    )
}
