package io.github.thesobiaakbar.adroit.billing

/** Which top corner of the paywall holds the close button (JSON `close.position`), mirrored in right-to-left. */
public enum class CloseButtonPosition {
    /** The top start corner (`"TOP_START"`). */
    TOP_START,

    /** The top end corner (`"TOP_END"`). */
    TOP_END,
}

/** When the system back gesture closes the paywall (JSON `close.back`). */
public enum class BackBehavior {
    /** Always (`"ALWAYS"`). */
    ALWAYS,

    /** Once the close button has appeared (`"AFTER_DELAY"`). With no close button, never. */
    AFTER_DELAY,

    /** Never (`"NEVER"`). With no close button either, this is a hard paywall; avoid it on onboarding. */
    NEVER,
}

/**
 * When and how the user can close a paywall (JSON `close`). Immutable.
 *
 * A paywall that fails to load can always be closed, whatever these settings say.
 *
 * @property delaySeconds JSON `close.delaySeconds`: 0 = the close button shows at once, N = after N seconds of the
 *   paywall being visible, [NO_CLOSE_BUTTON] (-1) = never. Values above 60 are treated as 60.
 * @property showCountdown JSON `close.showCountdown`: whether a countdown shows where the button will appear.
 * @property position JSON `close.position`: the corner of the close button.
 * @property back JSON `close.back`: when the back gesture closes the paywall.
 */
public class CloseBehavior @JvmOverloads constructor(
    public val delaySeconds: Int = 0,
    public val showCountdown: Boolean = false,
    public val position: CloseButtonPosition = CloseButtonPosition.TOP_END,
    public val back: BackBehavior = BackBehavior.ALWAYS,
) {
    init {
        require(delaySeconds >= NO_CLOSE_BUTTON) { "delaySeconds must be -1 or more, was $delaySeconds" }
    }

    private fun fields(): List<Any?> = listOf(delaySeconds, showCountdown, position, back)

    override fun equals(other: Any?): Boolean = other is CloseBehavior && fields() == other.fields()

    override fun hashCode(): Int = fields().hashCode()

    override fun toString(): String =
        "CloseBehavior(delaySeconds=$delaySeconds, showCountdown=$showCountdown, position=$position, back=$back)"

    /** Constants. */
    public companion object {
        /** [delaySeconds] value meaning the paywall has no close button. */
        public const val NO_CLOSE_BUTTON: Int = -1
    }
}
