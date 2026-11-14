package com.maxqiu.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class ThymeleafTests {
    @Autowired
    private WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void textAndUtextControlEscaping() throws Exception {
        assertThat(page("/thymeleaf/text")).contains(
                "id=\"escaped\">Native &lt;Syntax&gt; &amp; Thymeleaf</p>",
                "id=\"unescaped\"><em>Unescaped markup</em></div>");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void ifNegationEachAndTernaryEvaluateTheirInputs(boolean enabled) throws Exception {
        String html = page("/thymeleaf/conditions?enabled=" + enabled);
        assertThat(html.contains("id=\"enabled\"")).isEqualTo(enabled);
        assertThat(html.contains("id=\"disabled\"")).isEqualTo(!enabled);
        assertThat(tag(html, "choice").contains("active")).isEqualTo(enabled);
        assertThat(html).contains(enabled ? ">enabled</p>" : ">disabled</p>",
                "id=\"item-alpha\"", "id=\"item-beta\"").doesNotContain("id=\"empty\"");
        assertThat(Pattern.compile("class=\"item-code\"").matcher(html).results().count()).isEqualTo(2);
        assertThat(Pattern.compile("class=\"item-enabled\"").matcher(html).results().count()).isEqualTo(1);
    }

    @Test
    void eachHandlesAnEmptyCollection() throws Exception {
        assertThat(page("/thymeleaf/conditions?empty=true")).contains("id=\"empty\"", "Empty collection")
                .doesNotContain("id=\"item-alpha\"", "id=\"item-beta\"");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void attributesReadPropertiesAndCollectionMethodsOnDifferentModelTypes(boolean populated) throws Exception {
        String html = page("/thymeleaf/attributes?populated=" + populated);
        assertThat(tag(html, "concatenated-link")).contains("href=\"/thymeleaf/text?sample=7\"");
        assertThat(tag(html, "url-expression")).contains("href=\"/thymeleaf/text\"");
        assertThat(tag(html, "value")).contains(populated ? "value=\"Native &lt;Syntax&gt; &amp; Thymeleaf\"" : "value=\"\"");
        if (populated) {
            assertThat(tag(html, "value")).contains("title=\"Native &lt;Syntax&gt; &amp; Thymeleaf\"");
            assertThat(tag(html, "metadata")).contains("content=\"Native &lt;Syntax&gt; &amp; Thymeleaf\"");
        } else {
            assertThat(tag(html, "value")).doesNotContain("title=");
            assertThat(tag(html, "metadata")).doesNotContain("content=");
        }
        assertThat(tag(html, "checkbox-alpha").contains("checked=\"checked\"")).isEqualTo(populated);
        assertThat(tag(html, "checkbox-beta")).contains("value=\"beta\"", "title=\"beta\"").doesNotContain("checked=");
        assertThat(tag(html, "custom-attribute")).contains("data-syntax=\"attributes\"");
        assertThat(tag(html, "event")).contains("style=\"margin-left:14px\"", "onclick=\"select(7)\"");
    }

    @Test
    void expressionsResolveNestedGettersMethodsDatesAndOperators() throws Exception {
        String html = page("/thymeleaf/expressions");
        assertThat(html).contains("id=\"nested-property\">nested value</p>",
                "class=\"split-value\">alpha</span>", "class=\"split-value\">beta</span>",
                "id=\"contains\">true</p>", "id=\"date\">2026/11/14</p>",
                "id=\"date-time\">2026/11/14 10:20:30</p>", "id=\"arithmetic\">42</p>",
                "id=\"comparison\">less</p>", "id=\"concatenation\">value-7</p>");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void fragmentsInsertParametersReplaceLayoutsRemoveWrappersAndRecurse(boolean recursive) throws Exception {
        String html = page("/thymeleaf/fragments?recursive=" + recursive);
        assertThat(html).contains("<title>Fragment syntax</title>", "id=\"layout-marker\"", "id=\"fragment-slot\"",
                "id=\"fragment-message\">Fragment parameter value</p>", "id=\"fragment-script\"", "window.fragment = 'ready';")
                .doesNotContain("<section", "<th:block");
        assertThat(Pattern.compile("<script\\b").matcher(html).results().count()).isEqualTo(1);
        assertThat(html.contains("id=\"tree\"")).isEqualTo(recursive);
        if (recursive) {
            assertThat(tag(html, "node-root")).contains("data-depth=\"0\"");
            assertThat(tag(html, "node-branch")).contains("data-depth=\"1\"");
            assertThat(tag(html, "node-leaf")).contains("data-depth=\"2\"");
            assertThat(Pattern.compile("class=\"node-name\"").matcher(html).results().count()).isEqualTo(3);
        } else {
            assertThat(html).doesNotContain("id=\"node-root\"");
        }
    }

    @Test
    void textInliningEvaluatesTextAndPlainScriptExpressions() throws Exception {
        assertThat(page("/thymeleaf/inlining")).contains("id=\"inline-text\">inline value</p>",
                "window.syntax = {count: 3, position: 7};", "window.literal = 'inline value';");
    }

    @Test
    void contextExpressionsReadParametersAndNullableSessionProperties() throws Exception {
        MockHttpSession session = new MockHttpSession();
        assertThat(contextPage(session, "")).doesNotContain("id=\"parameter-present\"", "id=\"session-string\"", "id=\"session-property\"");
        assertThat(contextPage(session, "?flag")).contains("id=\"parameter-present\"");
        mvc.perform(post("/thymeleaf/context").session(session)).andExpect(status().isNoContent());
        assertThat(contextPage(session, "")).contains("id=\"session-string\">stored value</p>",
                "id=\"session-property\">Session property value</p>");
        mvc.perform(delete("/thymeleaf/context").session(session)).andExpect(status().isNoContent());
        assertThat(contextPage(session, "")).doesNotContain("id=\"session-string\"", "id=\"session-property\"");
    }

    private String page(String path) throws Exception {
        String html = mvc.perform(get(path)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).doesNotContain("Prototype", "[[${");
        assertThat(html).doesNotContainPattern("<[^>]*\\sth:[a-z-]+=\"");
        return html;
    }

    private String contextPage(MockHttpSession session, String query) throws Exception {
        return mvc.perform(get("/thymeleaf/context" + query).session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String tag(String html, String id) {
        var matcher = Pattern.compile("<[^>]+\\bid=\"" + Pattern.quote(id) + "\"[^>]*>").matcher(html);
        assertThat(matcher.find()).as("Rendered element #%s", id).isTrue();
        return matcher.group();
    }
}
