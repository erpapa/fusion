package io.github.kenneycode.fusion.process

import io.github.kenneycode.fusion.renderer.Renderer
import io.github.kenneycode.fusion.texture.Texture
import io.github.kenneycode.fusion.texture.TexturePool
import java.util.*

/**
 *
 * Coded by kenney
 *
 * http://www.github.com/kenneycode/fusion
 *
 * 渲染过程管理类，将Renderer按指定规则连接成Graph，并执行渲染过程
 *
 */

class RenderGraph private constructor() : Renderer {

    private val LAYER_SEPERATOR = null
    private var input = mutableListOf<Texture>()
    private var output: Texture? = null
    private val rendererNodeMap = HashMap<Renderer, Node>()
    private lateinit var rootNode: RendererNode

    companion object {

        fun create(): RenderGraph {
            return RenderGraph()
        }

    }

    /**
     *
     * 设置根renderer
     *
     * @param renderer  根Renderer
     *
     * @return 返回此RenderGraph
     *
     */
    fun setRootRenderer(renderer: Renderer): RenderGraph {
        rootNode = RendererNode(renderer)
        rendererNodeMap[renderer] = rootNode
        return this
    }

    /**
     *
     * 为一个Renderer连接一个后续Renderer
     *
     * @param pre   前一个Renderer
     * @param next  后一个Renderer
     *
     * @return 返回此RenderGraph
     *
     */
    fun connectRenderer(pre: Renderer, next: Renderer): RenderGraph {
        val preNode = rendererNodeMap[pre]!!
        if (!rendererNodeMap.containsKey(next)) {
            rendererNodeMap[next] = RendererNode(next, preNode.layer + 1)
        }
        rendererNodeMap[next]?.let { nextNode ->
            preNode.addNext(nextNode)
            nextNode.layer = if (preNode.layer + 1 > nextNode.layer) {
                preNode.layer + 1
            } else {
                nextNode.layer
            }
        }
        return this
    }

    /**
     *
     * 初始化，会对Graph中所有Node都调用其初始化方法
     *
     */
    override fun init() {
        val traversalQueue = LinkedList<Node>()
        traversalQueue.addLast(rootNode)
        while (!traversalQueue.isEmpty()) {
            val node = traversalQueue.removeFirst()
            when (node) {
                is RendererNode -> {
                    node.renderer.init()
                }
            }
            traversalQueue.addAll(node.nextNodes)
        }
    }

    /**
     *
     * 更新数据，会对Graph中所有Node都调用其更新数据的方法
     *
     * @param data 数据
     *
     * @return 是否需要执行当前渲染
     *
     */
    override fun update(data: MutableMap<String, Any>): Boolean {
        val traversalQueue = LinkedList<Node>()
        traversalQueue.addLast(rootNode)
        while (!traversalQueue.isEmpty()) {
            val node = traversalQueue.removeFirst()
            when (node) {
                is RendererNode -> {
                    node.needRender = node.renderer.update(data)
                }
            }
            traversalQueue.addAll(node.nextNodes)
        }
        return true
    }

    /**
     *
     * 设置输入
     *
     * @param frameBuffer 输入FrameBuffer
     *
     */
    override fun setInput(texture: Texture) {
        setInput(listOf(texture))
    }

    /**
     *
     * 设置输入
     *
     * @param frameBuffers 输入FrameBuffer
     */
    override fun setInput(textures: List<Texture>) {
        input.apply {
            clear()
            addAll(textures)
        }
    }

    override fun getOutput(): Texture? {
        return output
    }

    /**
     *
     * 设置输出
     *
     * @param frameBuffer 输出FrameBuffer
     *
     */
    override fun setOutput(texture: Texture?) {
        output = texture
    }

    /**
     *
     * 执行渲染
     *
     * @return 输出FrameBuffer
     */
    override fun render() {
        output = performTraversal(input)
    }

    private fun performTraversal(input: List<Texture>): Texture? {
        var intermediateOutput: Texture? = null
        (rootNode.input).addAll(input)
        val traversalQueue = LinkedList<Node?>()
        traversalQueue.addLast(rootNode)
        traversalQueue.addLast(LAYER_SEPERATOR)
        var currentLayer = 0
        while (!traversalQueue.isEmpty()) {
            val node = traversalQueue.removeFirst()
            if (node == LAYER_SEPERATOR) {
                ++currentLayer
                continue
            }
            if (node.layer != currentLayer) {
                continue
            }
            when (node) {
                is RendererNode -> {
                    node.renderer.setInput(node.input)
                    node.renderer.setOutput(
                        if (node.nextNodes.isEmpty()) {
                            output
                        } else {
                            TexturePool.obtainTexture(node.input.first().width, node.input.first().height)
                        }
                    )
                    node.renderer.render()
                    intermediateOutput = node.renderer.getOutput()
                }
            }
            node.input.clear()
            intermediateOutput?.let {
                if (node.nextNodes.isNotEmpty()) {
                    it.increaseRef(node.nextNodes.size - 1)
                }
                node.nextNodes.forEach { nextNode ->
                    nextNode.input.add(it)
                    traversalQueue.addLast(nextNode)
                }
            }
            traversalQueue.addLast(LAYER_SEPERATOR)
        }
        return intermediateOutput
    }

    /**
     *
     * 释放资源
     *
     */
    override fun release() {
        val traversalQueue = LinkedList<Node>()
        traversalQueue.addLast(rootNode)
        while (!traversalQueue.isEmpty()) {
            val node = traversalQueue.removeFirst()
            if (node is RendererNode) {
                node.renderer.release()
            }
            traversalQueue.addAll(node.nextNodes)
        }
    }

    /**
     *
     * Graph Node基类
     *
     */
    private open inner class Node(var layer: Int = 0) {

        var needRender = true
        var input = mutableListOf<Texture>()
        var nextNodes= mutableListOf<Node>()

        fun addNext(nextNode: Node) {
            nextNodes.add(nextNode)
        }

    }

    /**
     *
     * 渲染器Node类
     *
     */
    private inner class RendererNode(val renderer: Renderer, layer: Int = 0) : Node(layer)

}