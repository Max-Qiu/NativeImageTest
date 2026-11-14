package com.maxqiu.test.controller;

import com.maxqiu.test.dto.ThymeleafModels.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Each endpoint supplies fixed inputs for one category of template syntax. */
@Controller
@RequestMapping("/thymeleaf")
public class ThymeleafController {
    private static final String SAMPLE_TEXT = "Native <Syntax> & Thymeleaf";

    @GetMapping("/text")
    public String text(Model model) {
        model.addAttribute("text", SAMPLE_TEXT);
        model.addAttribute("markup", "<em>Unescaped markup</em>");
        return "thymeleaf/text";
    }

    @GetMapping("/conditions")
    public String conditions(Model model, @RequestParam(defaultValue = "true") boolean enabled,
                             @RequestParam(defaultValue = "false") boolean empty) {
        model.addAttribute("enabled", enabled);
        model.addAttribute("empty", empty);
        model.addAttribute("items", empty ? List.of() : List.of(new Item("alpha", true), new Item("beta", false)));
        return "thymeleaf/conditions";
    }

    @GetMapping("/attributes")
    public String attributes(Model model, @RequestParam(defaultValue = "true") boolean populated) {
        model.addAttribute("sample", populated ? new Value(SAMPLE_TEXT) : new NullableValue(null));
        model.addAttribute("tokens", List.of("alpha", "beta"));
        model.addAttribute("selected", new ArrayList<>(populated ? List.of("alpha") : List.of()));
        model.addAttribute("position", 7);
        model.addAttribute("target", "/thymeleaf/text");
        return "thymeleaf/attributes";
    }

    @GetMapping("/expressions")
    public String expressions(Model model) {
        model.addAttribute("wrapper", new Wrapper(new Value("nested value")));
        model.addAttribute("source", "alpha,beta");
        model.addAttribute("values", new ArrayList<>(List.of("beta")));
        model.addAttribute("timestamp", LocalDateTime.of(2026, 11, 14, 10, 20, 30));
        model.addAttribute("left", 6);
        model.addAttribute("right", 7);
        return "thymeleaf/expressions";
    }

    @GetMapping("/fragments")
    public String fragments(Model model, @RequestParam(defaultValue = "true") boolean recursive) {
        model.addAttribute("message", "Fragment parameter value");
        model.addAttribute("recursive", recursive);
        model.addAttribute("tree", new Node("root", List.of(new Node("branch", List.of(new Node("leaf", List.of()))))));
        return "thymeleaf/fragments";
    }

    @GetMapping("/inlining")
    public String inlining(Model model) {
        model.addAttribute("text", "inline value");
        model.addAttribute("count", 3);
        model.addAttribute("position", 7);
        return "thymeleaf/inlining";
    }

    @GetMapping("/context")
    public String context() {
        return "thymeleaf/context";
    }

    @PostMapping("/context")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setContext(HttpSession session) {
        session.setAttribute("syntaxValue", "stored value");
        session.setAttribute("syntaxObject", new Message("Session property value"));
    }

    @DeleteMapping("/context")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearContext(HttpSession session) {
        session.removeAttribute("syntaxValue");
        session.removeAttribute("syntaxObject");
    }
}
