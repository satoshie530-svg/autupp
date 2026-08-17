package com.privatestore.tvmanager.util

import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Los mensajes crudos de OkHttp/Java ("failed to connect to X port Y from Z port
 * W after Nms") exponen IPs y puertos internos y no dicen nada útil a quien está
 * viendo el TV. Se mapean los casos más comunes a español; el resto cae en un
 * mensaje genérico en vez de mostrar la excepción tal cual.
 */
fun Throwable.toUserMessage(): String = when (this) {
    is UnknownHostException -> "No se pudo resolver el servidor. Revisa la conexión a internet del TV."
    is SocketTimeoutException -> "El servidor tardó demasiado en responder."
    is SSLException -> "Error de seguridad al conectar con el servidor."
    is HttpException -> "El servidor respondió con un error (código ${code()})."
    is IOException -> "No se pudo conectar con el servidor."
    else -> "Ocurrió un error inesperado."
}
