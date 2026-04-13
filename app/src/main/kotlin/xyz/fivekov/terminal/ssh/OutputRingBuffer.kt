package xyz.fivekov.terminal.ssh

class OutputRingBuffer(private val maxChars: Int) {
    private val buffer = StringBuilder()

    @Synchronized
    fun append(text: String) {
        buffer.append(text)
        if (buffer.length > maxChars) {
            val excess = buffer.length - maxChars
            buffer.delete(0, excess)
        }
    }

    @Synchronized
    fun getContent(): String = buffer.toString()

    @Synchronized
    fun clear() = buffer.clear()
}
