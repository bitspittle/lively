package bitspittle.lively.swing

import bitspittle.lively.LiveScope
import bitspittle.lively.Lively
import bitspittle.lively.ObservingLive
import bitspittle.lively.SourceLive
import java.beans.PropertyChangeListener
import javax.swing.JComponent

fun Lively.wrapEnabled(component: JComponent): SourceLive<Boolean> {
    val liveEnabled = create(component.isEnabled)
    val enabledListener = PropertyChangeListener { liveEnabled.set(component.isEnabled) }
    component.addPropertyChangeListener("enabled", enabledListener)
    liveEnabled.onValueChanged += { component.isEnabled = it }
    liveEnabled.onFroze += { component.removePropertyChangeListener(enabledListener) }

    return liveEnabled
}

fun Lively.wrapEnabled(component: JComponent, observe: LiveScope.() -> Boolean): ObservingLive<Boolean> {
    val liveEnabled = create(observe)
    listen { component.isEnabled = liveEnabled.get() }

    return liveEnabled
}