package com.mlib.notes.ui

import com.mlib.future.BackgroundAnimationType
import com.mlib.future.FutureConfig
import com.mlib.future.FutureColors
import com.mlib.future.hexColor

data class ThemeSet(val name: String, val light: FutureConfig, val dark: FutureConfig)

val themeSets = listOf(
	ThemeSet("Masem", FutureConfig.MasemLight, FutureConfig.MasemDark),
	ThemeSet("Aurora", FutureConfig.AuroraLight, FutureConfig.AuroraDark),
	ThemeSet("Julee", FutureConfig.JuleeLight, FutureConfig.JuleeDark),
	ThemeSet("Autumn", FutureConfig.AutumnLight, FutureConfig.AutumnDark),
	ThemeSet("Sunrise", FutureConfig.SunriseLight, FutureConfig.SunriseDark),
	ThemeSet("Sunset", FutureConfig.SunsetLight, FutureConfig.SunsetDark),
	ThemeSet("Painting", FutureConfig.PaintingLight, FutureConfig.PaintingDark),
	ThemeSet("Charcoal", FutureConfig.CharcoalLight, FutureConfig.CharcoalDark),
	ThemeSet("Celeste", FutureConfig.CelesteLight, FutureConfig.CelesteDark),
	ThemeSet(
		"Custom",
		FutureConfig(
			colors = FutureColors(primary = hexColor("#00FF88"), globalBg = hexColor("#001122")),
			backgroundAnimation = BackgroundAnimationType.CYBER_GRID
		),
		FutureConfig(
			colors = FutureColors(primary = hexColor("#00FF88"), globalBg = hexColor("#001122")),
			backgroundAnimation = BackgroundAnimationType.CYBER_GRID
		)
	)
)
