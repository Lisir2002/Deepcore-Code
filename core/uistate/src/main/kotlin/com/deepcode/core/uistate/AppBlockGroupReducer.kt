package com.deepcode.core.uistate

/**
 * 执行组聚组归约器（§6.8.3 / §6.8.8 状态机）。
 *
 * 输入：`TranscriptReducer` 产出的**扁平** `RenderBlock` 列表。
 * 输出：把**连续**的 thinking / tool_use 块聚为 `RenderBlock.Group`；text 块独立截断分组，
 * 即正文/通知/收尾块会打断一组，不进入组内。单个 dangling 块（前后无同类邻居）不包组，
 * 保持原样直通——避免没必要的组壳。
 *
 * 纯 Kotlin、不碰 Compose（M0 铁律），可 JVM 单测。
 */
object AppBlockGroupReducer {

    /** @return 聚组后的渲染块列表。列表为空直接原样返回。 */
    fun group(blocks: List<RenderBlock>): List<RenderBlock> {
        if (blocks.isEmpty()) return blocks

        val out = mutableListOf<RenderBlock>()
        var pending = mutableListOf<RenderBlock>()

        fun flush() {
            when (pending.size) {
                0 -> Unit
                1 -> out.add(pending.first())
                else -> {
                    val key = "group-${pending.first().key}"
                    out.add(RenderBlock.Group(key = key, blocks = pending.toList()))
                }
            }
            pending = mutableListOf()
        }

        for (block in blocks) {
            when (block) {
                is RenderBlock.Thinking,
                is RenderBlock.ToolInvocation,
                -> pending.add(block)
                else -> {
                    flush()
                    out.add(block)
                }
            }
        }
        flush()
        return out
    }
}