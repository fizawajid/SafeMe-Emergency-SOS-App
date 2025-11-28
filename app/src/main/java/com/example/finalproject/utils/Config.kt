package com.example.finalproject.utils

object Config {
    // Server Configuration - Update only this IP/URL
    const val PHP_BASE_URL = "http://192.168.1.131/safety-alerts/api"

    // API Endpoints - These will be built automatically
    const val UPLOAD_IMAGE_ENDPOINT = "$PHP_BASE_URL/upload_image.php"
    const val CREATE_ALERT_ENDPOINT = "$PHP_BASE_URL/create_alert.php"

    // Add other endpoints here as you create more features
    const val GET_ALERTS_ENDPOINT = "$PHP_BASE_URL/get_alerts.php"
    const val DELETE_IMAGE_ENDPOINT = "$PHP_BASE_URL/delete_image.php"

    // EmailJS Configuration (if needed elsewhere)
    const val EMAILJS_SERVICE_ID = "service_7c31t4s"
    const val EMAILJS_TEMPLATE_ID = "template_6woyk8b"
    const val EMAILJS_PUBLIC_KEY = "z0XPnqySWvklrNwlF"

    // App Constants
    const val MAX_IMAGE_SIZE_KB = 1024 // 1MB max image size
    const val IMAGE_COMPRESSION_QUALITY = 70
}