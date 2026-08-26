import io.gitlab.arturbosch.detekt.api.*
import org.jetbrains.kotlin.psi.KtCallExpression

class NoForwardMessagesRule(config: Config) : Rule(config) {
    override val issue = Issue(
        id = "NoForwardMessages",
        severity = Severity.Defect,
        description = "nativeForward() is banned outside the allow-listed file.",
        debt = Debt.TWENTY_MINS
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (expression.calleeExpression?.text == "nativeForward" &&
            !expression.containingFile.name.endsWith("ForwardAsNewAllowlist.kt") &&
            !expression.containingFile.name.endsWith("TdLibEngine.kt")) {
            report(CodeSmell(issue, Entity.from(expression),
                "nativeForward() is banned outside the allow-listed file. Use forwardAsNew() which re-sends via sendMessage()."))
        }
    }
}
