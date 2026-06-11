package com.lauro.petguardian

object AppConfig {
    // Configure antes de gerar o APK. Nunca publique tokens reais.
    const val DASHBOARD_URL = "https://SEU_DASHBOARD.vercel.app"
    const val DASHBOARD_API_URL = "https://SEU_PROJETO.supabase.co/functions/v1/dashboard-data"
    const val COMMAND_API_URL = "https://SEU_PROJETO.supabase.co/functions/v1/control-device"
    const val PHOTOS_API_URL = "https://SEU_PROJETO.supabase.co/functions/v1/photos"
    const val DASHBOARD_TOKEN = "SEU_DASHBOARD_TOKEN"
}
