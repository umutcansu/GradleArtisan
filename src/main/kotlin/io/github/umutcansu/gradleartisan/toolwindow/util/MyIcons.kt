package io.github.umutcansu.gradleartisan.toolwindow.util

import com.intellij.openapi.util.IconLoader


object MyIcons {
    @JvmField
    val Failed = IconLoader.getIcon("/icons/failed.svg", javaClass)
    @JvmField
    val Star = IconLoader.getIcon("/icons/star.svg", javaClass)
    @JvmField
    val StarEmpty = IconLoader.getIcon("/icons/starEmpty.svg", javaClass)
    @JvmField
    val Success = IconLoader.getIcon("/icons/success.svg", javaClass)
    @JvmField
    val Add = IconLoader.getIcon("/icons/add.svg", javaClass)
    @JvmField
    val Remove = IconLoader.getIcon("/icons/remove.svg", javaClass)
    @JvmField
    val Property = IconLoader.getIcon("/icons/property.svg", javaClass)
}