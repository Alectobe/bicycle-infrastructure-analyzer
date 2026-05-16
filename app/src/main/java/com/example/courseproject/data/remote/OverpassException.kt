package com.example.courseproject.data.remote

/** Ошибка получения или разбора данных Overpass API. */
class OverpassException(message: String, cause: Throwable? = null) : Exception(message, cause)
