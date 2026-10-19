package com.healthcare.inventory.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Serves the Thymeleaf pages. The pages themselves are static shells that
 * call the REST API (/api/medicines/**) via fetch() to load and mutate data.
 */
@Controller
public class MedicineViewController {

    @GetMapping({ "/", "/inventory", "/medicines" })
    public String inventory() {
        return "inventory";
    }

    @GetMapping({ "/billing", "/medicines/billing" })
    public String billing() {
        return "billing";
    }

    @GetMapping({ "/inventory/add", "/medicines/add" })
    public String addForm(Model model) {
        model.addAttribute("mode", "add");
        model.addAttribute("medicineId", "");
        return "medicine-form";
    }

    @GetMapping({ "/inventory/edit/{id}", "/medicines/edit/{id}" })
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("mode", "edit");
        model.addAttribute("medicineId", id);
        return "medicine-form";
    }
}
