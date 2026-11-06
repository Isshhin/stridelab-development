package io.stridelab.privatealpha

import android.util.Patterns
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal enum class TracksideAuthPage { SignIn, SignUp, Reset }

private val authLime = Color(0xFFD8FB70)
private val authBlack = Color(0xFF080B0A)
private val authWhite = Color(0xFFF2F5F2)
private val authMuted = Color(0xFFAAB4B0)
private val authCard = Color(0xFF101514)
private val authBorder = Color(0xFF43504A)

/** Focused, scrollable, keyboard-safe authentication with no legacy promo card. */
@Composable
internal fun TracksideAuthScreen(
    page: TracksideAuthPage,
    busy: Boolean,
    feedback: String?,
    onBack: () -> Unit,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Unit,
    onReset: (String) -> Unit,
    onSignInPage: () -> Unit,
    onSignUpPage: () -> Unit,
    onResetPage: () -> Unit
) {
    var name by rememberSaveable(page) { mutableStateOf("") }
    var email by rememberSaveable(page) { mutableStateOf("") }
    // Password fields intentionally use non-saveable memory only.
    var password by remember(page) { mutableStateOf("") }
    var confirm by remember(page) { mutableStateOf("") }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                password = ""
                confirm = ""
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {
            lifecycle.lifecycle.removeObserver(observer)
            password = ""
            confirm = ""
        }
    }
    val focus = LocalFocusManager.current
    val emailValid = email.isNotBlank() &&
        Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val canSubmit = !busy && when (page) {
        TracksideAuthPage.SignIn -> emailValid && password.isNotEmpty()
        TracksideAuthPage.SignUp -> emailValid && name.isNotBlank() &&
            password.length >= 8 && password == confirm
        TracksideAuthPage.Reset -> emailValid
    }
    val submit: () -> Unit = {
        if (canSubmit) {
            focus.clearFocus()
            when (page) {
                TracksideAuthPage.SignIn -> onSignIn(email.trim(), password)
                TracksideAuthPage.SignUp -> onSignUp(name.trim(), email.trim(), password)
                TracksideAuthPage.Reset -> onReset(email.trim())
            }
            password = ""
            confirm = ""
        }
    }
    Box(Modifier.fillMaxSize().background(authBlack).testTag("auth-" + page.name.lowercase())) {
        Canvas(Modifier.align(Alignment.TopEnd).fillMaxWidth().height(164.dp)) {
            drawArc(
                color = authLime.copy(alpha = .26f),
                startAngle = 201f, sweepAngle = 80f, useCenter = false,
                topLeft = Offset(size.width * .52f, -size.height * .95f),
                size = Size(size.width * .88f, size.height * 2f),
                style = Stroke(1.5.dp.toPx())
            )
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "←", color = authWhite, fontSize = 26.sp,
                    modifier = Modifier.size(48.dp).clickable(onClick = onBack)
                        .semantics { contentDescription = "Back" }
                        .wrapContentSize(Alignment.Center).testTag("auth-back")
                )
                Spacer(Modifier.width(2.dp))
                AuthMark()
                Spacer(Modifier.width(10.dp))
                Text(
                    "STRIDELAB", fontSize = 16.sp, color = authWhite,
                    letterSpacing = 1.sp, fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(58.dp))
            Text(
                when (page) {
                    TracksideAuthPage.SignIn -> "SIGN IN"
                    TracksideAuthPage.SignUp -> "CREATE ACCOUNT"
                    TracksideAuthPage.Reset -> "RESET PASSWORD"
                },
                color = authMuted, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                when (page) {
                    TracksideAuthPage.SignIn -> "Welcome back"
                    TracksideAuthPage.SignUp -> "Get started"
                    TracksideAuthPage.Reset -> "Reset password"
                },
                color = authWhite, fontSize = 35.sp, lineHeight = 41.sp,
                fontWeight = FontWeight.Bold, letterSpacing = (-.8).sp
            )
            Spacer(Modifier.height(30.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(25.dp),
                color = authCard, border = BorderStroke(1.dp, authBorder)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 25.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    if (page == TracksideAuthPage.SignUp) {
                        AuthField(name, { name = it }, "Name", AuthSymbol.Person, "signup-name", !busy)
                    }
                    AuthField(
                        email, { email = it }, "Email", AuthSymbol.Email,
                        if (page == TracksideAuthPage.SignUp) "signup-email" else "email",
                        !busy, keyboardType = KeyboardType.Email
                    )
                    if (page != TracksideAuthPage.Reset) {
                        AuthField(
                            password, { password = it }, "Password", AuthSymbol.Lock, "password",
                            !busy, isPassword = true,
                            imeAction = if (page == TracksideAuthPage.SignIn)
                                ImeAction.Done else ImeAction.Next,
                            onDone = if (page == TracksideAuthPage.SignIn) submit else ({})
                        )
                        if (page == TracksideAuthPage.SignUp) {
                            AuthField(
                                confirm, { confirm = it }, "Confirm password", AuthSymbol.Lock,
                                "signup-confirm-password", !busy,
                                isPassword = true, imeAction = ImeAction.Done, onDone = submit
                            )
                        } else {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                TextButton(onClick = onResetPage, enabled = !busy) {
                                    Text("Forgot password?", color = authLime, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    if (!feedback.isNullOrBlank() && !busy) {
                        Text(
                            feedback, color = authMuted, fontSize = 12.sp, lineHeight = 17.sp,
                            modifier = Modifier.testTag("auth-feedback")
                        )
                    }
                    Spacer(Modifier.height(if (page == TracksideAuthPage.SignIn) 4.dp else 12.dp))
                    Button(
                        onClick = submit, enabled = canSubmit,
                        modifier = Modifier.fillMaxWidth().height(56.dp).testTag(
                            when (page) {
                                TracksideAuthPage.SignIn -> "submit-signin"
                                TracksideAuthPage.SignUp -> "submit-signup"
                                TracksideAuthPage.Reset -> "submit-reset"
                            }
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = authLime, contentColor = authBlack,
                            disabledContainerColor = Color(0xFF252C29),
                            disabledContentColor = authMuted
                        ),
                        shape = RoundedCornerShape(32.dp)
                    ) {
                        Box(Modifier.fillMaxWidth()) {
                            Text(
                                when (page) {
                                    TracksideAuthPage.SignIn -> "Sign in"
                                    TracksideAuthPage.SignUp -> "Create account"
                                    TracksideAuthPage.Reset -> "Send reset link"
                                },
                                fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                            Text("→", fontSize = 25.sp, lineHeight = 25.sp,
                                modifier = Modifier.align(Alignment.CenterEnd))
                        }
                    }
                }
            }
            Spacer(Modifier.height(19.dp))
            when (page) {
                TracksideAuthPage.SignIn -> AuthLink("Create account", onSignUpPage)
                TracksideAuthPage.SignUp -> AuthLink("Already have an account?  Sign in", onSignInPage)
                TracksideAuthPage.Reset -> AuthLink("Back to sign in", onSignInPage)
            }
        }
    }
}

@Composable private fun AuthMark() {
    Canvas(Modifier.size(28.dp)) {
        val stroke = 4.dp.toPx()
        drawLine(authLime, Offset(size.width * .15f, size.height * .75f),
            Offset(size.width * .57f, size.height * .15f), stroke, cap = StrokeCap.Round)
        drawLine(authLime, Offset(size.width * .50f, size.height * .85f),
            Offset(size.width * .91f, size.height * .25f), stroke, cap = StrokeCap.Round)
    }
}

@Composable private fun AuthLink(label: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TextButton(onClick = onClick) {
            Text(label, color = authLime, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
    }
}

private enum class AuthSymbol { Email, Person, Lock, Eye }

@Composable private fun AuthField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    kind: AuthSymbol,
    tag: String,
    enabled: Boolean,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {}
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onChange, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).testTag(tag),
        placeholder = { Text(hint, fontSize = 14.sp, color = authMuted) },
        leadingIcon = { AuthGlyph(kind) },
        trailingIcon = if (isPassword) {
            {
                IconButton(
                    onClick = { visible = !visible },
                    modifier = Modifier.semantics {
                        contentDescription = if (visible) "Hide " + hint else "Show " + hint
                    }
                ) { AuthGlyph(AuthSymbol.Eye) }
            }
        } else null,
        textStyle = TextStyle(fontSize = 15.sp, color = authWhite),
        singleLine = true,
        visualTransformation = if (isPassword && !visible)
            PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        shape = RoundedCornerShape(20.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = authLime,
            unfocusedBorderColor = authBorder,
            disabledBorderColor = authBorder,
            focusedContainerColor = authBlack,
            unfocusedContainerColor = authBlack,
            disabledContainerColor = authBlack,
            cursorColor = authLime,
            focusedLeadingIconColor = authWhite,
            unfocusedLeadingIconColor = authWhite,
            focusedTrailingIconColor = authWhite,
            unfocusedTrailingIconColor = authWhite,
            focusedTextColor = authWhite,
            unfocusedTextColor = authWhite
        )
    )
}

@Composable private fun AuthGlyph(kind: AuthSymbol) {
    Canvas(Modifier.size(20.dp)) {
        val c = authWhite
        val w = size.width
        val h = size.height
        val sw = 1.6.dp.toPx()
        when (kind) {
            AuthSymbol.Email -> {
                drawRoundRect(c, Offset(w * .08f, h * .20f), Size(w * .84f, h * .60f),
                    CornerRadius(w * .06f), style = Stroke(sw))
                drawLine(c, Offset(w * .12f, h * .28f), Offset(w * .50f, h * .55f), sw)
                drawLine(c, Offset(w * .88f, h * .28f), Offset(w * .50f, h * .55f), sw)
            }
            AuthSymbol.Person -> {
                drawCircle(c, radius = w * .17f, center = Offset(w * .5f, h * .30f),
                    style = Stroke(sw))
                drawArc(c, 195f, 150f, false,
                    topLeft = Offset(w * .12f, h * .40f),
                    size = Size(w * .76f, h * .64f), style = Stroke(sw))
            }
            AuthSymbol.Lock -> {
                drawRoundRect(c, Offset(w * .20f, h * .43f), Size(w * .60f, h * .47f),
                    CornerRadius(w * .06f), style = Stroke(sw))
                drawArc(c, 180f, 180f, false,
                    topLeft = Offset(w * .32f, h * .08f),
                    size = Size(w * .36f, h * .58f), style = Stroke(sw))
            }
            AuthSymbol.Eye -> {
                val outline = Path().apply {
                    moveTo(w * .07f, h * .50f)
                    quadraticTo(w * .50f, h * -.04f, w * .93f, h * .50f)
                    quadraticTo(w * .50f, h * 1.04f, w * .07f, h * .50f)
                    close()
                }
                drawPath(outline, c, style = Stroke(sw))
                drawCircle(c, radius = w * .13f, center = Offset(w * .50f, h * .50f))
            }
        }
    }
}
