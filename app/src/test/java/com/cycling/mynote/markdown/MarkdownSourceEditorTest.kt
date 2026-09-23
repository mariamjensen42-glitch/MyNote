package com.cycling.mynote.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownSourceEditorTest {

    @Test
    fun `toggling an unchecked task checks it`() {
        val source = "- [ ] 写测试\n- [ ] 跑测试\n"

        val toggled = MarkdownSourceEditor.toggleTask(source, 0)

        assertEquals("- [x] 写测试\n- [ ] 跑测试\n", toggled)
    }

    @Test
    fun `toggling a checked task unchecks it`() {
        assertEquals("  - [ ] 嵌套", MarkdownSourceEditor.toggleTask("  - [x] 嵌套", 0))
        assertEquals("* [ ] 星号", MarkdownSourceEditor.toggleTask("* [X] 星号", 0))
    }

    @Test
    fun `the toggled mark keeps the line's length`() {
        val source = "- [ ] 写测试"
        val toggled = MarkdownSourceEditor.toggleTask(source, 0)

        assertEquals(source.length, toggled?.length)
    }

    @Test
    fun `tasks below front matter are addressed by their raw line`() {
        val source = "---\ntags: [a]\n---\n\n# 标题\n- [ ] 任务\n"

        val task = MarkdownParser.parse(source).blocks.filterIsInstance<MarkdownBlock.Task>().single()
        assertEquals(5, task.line)
        assertEquals("---\ntags: [a]\n---\n\n# 标题\n- [x] 任务\n", MarkdownSourceEditor.toggleTask(source, task.line))
    }

    @Test
    fun `a line that is not a task is left alone`() {
        assertNull(MarkdownSourceEditor.toggleTask("- 普通条目", 0))
        assertNull(MarkdownSourceEditor.toggleTask("正文", 0))
        assertNull(MarkdownSourceEditor.toggleTask("- [ ] 任务", 9))
    }

    @Test
    fun `enter continues a bullet with the same marker`() {
        // The line break is already in the text by the time this runs — a soft keyboard commits it as
        // text rather than as a key — so the caret sits on a fresh empty line whose item above it says
        // what to start with.
        val source = "- 第一项\n"
        val edit = MarkdownSourceEditor.continueList(source, source.length)!!

        assertEquals("- 第一项\n- ", edit.text)
        assertEquals(edit.text.length, edit.selectionStart)
    }

    @Test
    fun `enter continues a task unchecked even after a finished one`() {
        val source = "- [x] 做完了\n"
        val edit = MarkdownSourceEditor.continueList(source, source.length)!!

        assertEquals("- [x] 做完了\n- [ ] ", edit.text)
    }

    @Test
    fun `enter counts an ordered list up and keeps its indentation`() {
        val source = "  3. 第三项\n"
        val edit = MarkdownSourceEditor.continueList(source, source.length)!!

        assertEquals("  3. 第三项\n  4. ", edit.text)
    }

    @Test
    fun `enter on an empty item ends the list`() {
        val source = "- 第一项\n- \n"
        val edit = MarkdownSourceEditor.continueList(source, source.length)!!

        assertEquals("- 第一项\n", edit.text)
        assertEquals(6, edit.selectionStart)
    }

    @Test
    fun `enter outside a list is left to the field`() {
        assertNull(MarkdownSourceEditor.continueList("普通段落\n", 5))
    }

    @Test
    fun `a line that already has text does not continue anything`() {
        assertNull(MarkdownSourceEditor.continueList("- 第一项\n半行", 8))
    }

    @Test
    fun `enter on the first line has nothing to continue`() {
        assertNull(MarkdownSourceEditor.continueList("\n", 1))
    }

    @Test
    fun `tab indents every line the selection touches and ends up selecting them`() {
        val source = "一\n二\n三"
        val edit = MarkdownSourceEditor.indent(source, 0, source.length, outdent = false)

        assertEquals("  一\n  二\n  三", edit.text)
        assertEquals(0 to edit.text.length, edit.selectionStart to edit.selectionEnd)
    }

    @Test
    fun `tab with a caret indents that line and keeps the caret's column`() {
        val source = "一\n二\n三"
        val edit = MarkdownSourceEditor.indent(source, 3, 3, outdent = false)

        assertEquals("一\n  二\n三", edit.text)
        assertEquals(5, edit.selectionStart)
    }

    @Test
    fun `shift tab removes one indent`() {
        val source = "  一\n  二"

        assertEquals("一\n二", MarkdownSourceEditor.indent(source, 0, source.length, outdent = true).text)
    }

    @Test
    fun `shift tab on a line with no indent leaves the text alone`() {
        val source = "一\n二"

        assertEquals(source, MarkdownSourceEditor.indent(source, 0, source.length, outdent = true).text)
    }

    @Test
    fun `a picture attached at the end of a heading becomes its own block`() {
        val source = "# 我的标题"
        val edit = MarkdownSourceEditor.attachImage(source, source.length, source.length, "attachments/1.jpg")

        assertEquals("# 我的标题\n\n![](attachments/1.jpg)", edit.text)
    }

    @Test
    fun `a picture attached over a selection uses it as the alt text`() {
        val source = "一只猫"

        val edit = MarkdownSourceEditor.attachImage(source, 0, source.length, "attachments/1.jpg")

        assertEquals("![一只猫](attachments/1.jpg)", edit.text)
    }

    @Test
    fun `a picture attached at the start of a line leaves the line's text as its own block`() {
        val source = "上文\n\n下文"
        val caret = source.indexOf("下文")

        val edit = MarkdownSourceEditor.attachImage(source, caret, caret, "pic.png")

        assertEquals("上文\n\n![](pic.png)\n\n下文", edit.text)
        // The caret ends up after the picture, not back inside the text it was pushed off the line.
        assertEquals("上文\n\n![](pic.png)".length, edit.selectionStart)
    }

    @Test
    fun `a picture attached inside blank space does not add blank lines`() {
        val source = "上文\n  \n下文"
        val caret = source.indexOf("  ") + 1

        val edit = MarkdownSourceEditor.attachImage(source, caret, caret, "pic.png")

        assertEquals("上文\n ![](pic.png) \n下文", edit.text)
    }

    @Test
    fun `a picture attached mid-line splits the paragraph instead of joining it`() {
        val source = "前半后半"

        val edit = MarkdownSourceEditor.attachImage(source, 2, 2, "pic.png")

        assertEquals("前半\n\n![](pic.png)\n\n后半", edit.text)
    }

    @Test
    fun `the caret lands after the picture just written`() {
        val edit = MarkdownSourceEditor.attachImage("", 0, 0, "pic.png")

        assertEquals("![](pic.png)", edit.text)
        assertEquals(edit.text.length, edit.selectionStart)
    }

    @Test
    fun `the parser reads back what attachImage wrote as a picture block`() {
        val source = "# 标题"
        val edit = MarkdownSourceEditor.attachImage(source, source.length, source.length, "attachments/1.jpg")

        val images = MarkdownParser.parse(edit.text).blocks
            .filterIsInstance<MarkdownBlock.Paragraph>()
            .flatMap { it.spans.images() }

        assertEquals(listOf("attachments/1.jpg" to ""), images)
    }

    @Test
    fun `a picture attached at the very top of a note goes below the front matter`() {
        val source = "---\ncreated: \"2026-09-23\"\n---\n\n# 标题\n"

        val edit = MarkdownSourceEditor.attachImage(source, 0, 0, "attachments/1.jpg")

        assertEquals("---\ncreated: \"2026-09-23\"\n---\n\n![](attachments/1.jpg)\n\n# 标题\n", edit.text)
        assertEquals("2026-09-23", FrontMatterParser.parse(edit.text).frontMatter.created)
    }

    @Test
    fun `a note with no front matter is unaffected`() {
        val edit = MarkdownSourceEditor.attachImage("正文", 0, 0, "pic.png")

        assertEquals("![](pic.png)\n\n正文", edit.text)
    }
}
