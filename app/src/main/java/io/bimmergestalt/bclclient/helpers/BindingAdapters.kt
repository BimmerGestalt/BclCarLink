package io.bimmergestalt.bclclient.helpers

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.databinding.BindingAdapter
import com.google.android.material.animation.ArgbEvaluatorCompat
import java.util.*


@BindingAdapter("android:src")
fun setImageViewDrawableResource(view: ImageView, drawable: (Context.() -> Drawable?)?) {
    drawable ?: return
    view.setImageDrawable(view.context.run(drawable))
}
@BindingAdapter("android:src")
fun setImageViewResource(view: ImageView, resource: Int) {
    view.setImageResource(resource)
}

@BindingAdapter("android:foregroundTint")
fun setForegroundTint(view: View, value: (Context.() -> Int)?) {
    value ?: return
    val color = view.context.run(value)
    setForegroundTint(view, color)
}
@BindingAdapter("android:foregroundTint")
fun setForegroundTint(view: View, color: Int) {
    val startColor = view.foregroundTintList?.defaultColor
    if (startColor != color) {
        if (startColor == null) {
            view.foregroundTintList = ColorStateList.valueOf(color)
        } else {
            ValueAnimator.ofObject(ArgbEvaluatorCompat(), startColor, color).apply {
                addUpdateListener { view.foregroundTintList = ColorStateList.valueOf(it.animatedValue as Int) }
                start()
            }
        }
    }
}
@BindingAdapter("tint")
fun setImageTint(view: ImageView, value: (Context.() -> Int)?) {
    value ?: return
    val color = view.context.run(value)
    setImageTint(view, color)
}
@BindingAdapter("tint")
fun setImageTint(view: ImageView, color: Int) {
    val startColor = view.imageTintList?.defaultColor
    if (startColor != color) {
        if (startColor == null) {
            view.imageTintList = ColorStateList.valueOf(color)
        } else {
            ValueAnimator.ofObject(ArgbEvaluatorCompat(), startColor, color).apply {
                addUpdateListener { view.imageTintList = ColorStateList.valueOf(it.animatedValue as Int) }
                start()
            }
        }
    }
}

// Dynamic text
@BindingAdapter("android:text")
fun setText(view: TextView, value: (Context.() -> String)?) {
    view.text = if (value != null) {
        view.context.run(value)
    } else {
        ""
    }
}

// set an animator
val CANCELLABLE_ANIMATORS = WeakHashMap<View, Animator>()
@BindingAdapter("animator")
fun setAnimator(view: View, value: Animator?) {
    CANCELLABLE_ANIMATORS[view]?.cancel()
    if (value != null) {
        value.setTarget(view)
        value.start()
        CANCELLABLE_ANIMATORS[view] = value
    } else {
        view.animation?.cancel()
        view.clearAnimation()
    }
}