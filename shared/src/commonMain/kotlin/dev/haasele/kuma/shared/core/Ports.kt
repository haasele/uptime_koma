package dev.haasele.koma.shared.core

/** Returns true when nothing is currently listening on the given TCP port. */
expect fun isTcpPortFree(port: Int): Boolean
