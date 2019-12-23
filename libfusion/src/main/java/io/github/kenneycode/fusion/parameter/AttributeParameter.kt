package io.github.kenneycode.fusion.parameter

import android.opengl.GLES20

/**
 *
 * Coded by kenney
 *
 * http://www.github.com/kenneycode/fusion
 *
 * Shader attribute参数基类
 *
 */

abstract class AttributeParameter(key : String) : Parameter(key) {

    /**
     *
     * 绑定参数
     *
     * @param program GL Program
     *
     */
    override fun bind(program: Int) {
        if (location < 0) {
            location = GLES20.glGetAttribLocation(program, key)
        }
        onBind(location)
    }

}