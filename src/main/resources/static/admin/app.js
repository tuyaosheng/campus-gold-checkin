(function () {
    const storageKey = "checkin_admin_auth";
    const authText = localStorage.getItem(storageKey);
    if (!authText) {
        location.replace("/admin/login.html");
        return;
    }

    const auth = JSON.parse(authText);
    const isAdmin = auth.roleCode === "ADMIN";
    const roleLabelMap = {
        ADMIN: "管理员",
        STAFF: "行政",
        GRADE_LEADER: "级长",
        TEACHER: "班主任"
    };
    const sections = ["overview", "leaderboard", "export", "correction", "password", "accounts", "student-order", "roster", "semester", "quotes", "rank", "links"];
    let semesterStartDate = null;
    let currentWeek = 0;
    let lastStudentImportResult = null;
    let lastUserImportResult = null;
    let classOptions = [];
    let accessBaseUrl = location.origin;
    let currentLeaderboardClassId = null;
    let leaderboardRowsCache = [];
    let studentQualityRowsCache = [];
    let studentQualityPage = 1;
    const studentQualityPageSize = 100;
    const navLinks = Array.from(document.querySelectorAll(".nav-link"));
    const adminOnlyItems = Array.from(document.querySelectorAll(".admin-only"));
    const userEditModal = document.getElementById("userEditModal");
    const correctionCheckinModal = document.getElementById("correctionCheckinModal");

    adminOnlyItems.forEach(item => {
        if (!isAdmin) {
            item.classList.add("hidden");
        }
    });

    document.getElementById("currentUser").textContent = auth.realName || auth.username || "--";
    document.getElementById("currentRole").textContent = roleLabelMap[auth.roleCode] || auth.roleCode;
    document.getElementById("headingSub").textContent = isAdmin
        ? "当前账号拥有系统管理、导入、链接维护与全校导出权限。"
        : (auth.roleCode === "STAFF"
            ? "当前账号可查看和导出全校数据，但不具备系统配置权限。"
            : (auth.roleCode === "GRADE_LEADER"
                ? `当前账号可查看和导出 ${auth.gradeName || "对应年级"} 数据。`
                : "当前账号仅可查看和导出本班数据。"));

    document.getElementById("logoutBtn").addEventListener("click", () => {
        localStorage.removeItem(storageKey);
        location.replace("/admin/login.html");
    });

    navLinks.forEach(button => {
        button.addEventListener("click", () => {
            if (auth.mustChangePassword && button.dataset.section !== "password") {
                setStatus("passwordStatus", "首次登录后必须先修改密码，暂不允许切换到其他页面。", "error");
                activateSection("password");
                return;
            }
            activateSection(button.dataset.section);
        });
    });

    function activateSection(target) {
        if (!target) {
            return;
        }
        if (auth.mustChangePassword && target !== "password") {
            target = "password";
        }
        sections.forEach(name => {
            const section = document.getElementById(`section-${name}`);
            if (section) {
                section.classList.toggle("hidden", name !== target);
            }
        });
        navLinks.forEach(item => item.classList.toggle("active", item.dataset.section === target));
    }

    function openModal(modal) {
        if (!modal) {
            return;
        }
        modal.classList.remove("hidden");
        document.body.style.overflow = "hidden";
    }

    function closeModal(modal) {
        if (!modal) {
            return;
        }
        modal.classList.add("hidden");
        document.body.style.overflow = "";
    }

    function setDefaultDate(inputId) {
        document.getElementById(inputId).value = new Date().toISOString().slice(0, 10);
    }

    setDefaultDate("leaderboardDate");
    setDefaultDate("exportDate");
    setDefaultDate("correctionDate");

    document.querySelectorAll("[data-picker-target]").forEach(button => {
        button.addEventListener("click", () => {
            const input = document.getElementById(button.dataset.pickerTarget);
            if (!input) {
                return;
            }
            input.focus();
            if (typeof input.showPicker === "function") {
                input.showPicker();
            }
        });
    });



    function formatQualityScore(value) {
        const num = Number(value || 0);
        return num.toFixed(2);
    }
    function setStatus(elementId, text, type) {
        const element = document.getElementById(elementId);
        if (!element) {
            return;
        }
        element.innerHTML = text;
        element.classList.remove("success", "error");
        if (type) {
            element.classList.add(type);
        }
    }

    async function copyText(text) {
        if (!text) {
            throw new Error("复制内容为空。");
        }
        if (navigator.clipboard && window.isSecureContext) {
            await navigator.clipboard.writeText(text);
            return;
        }
        const textArea = document.createElement("textarea");
        textArea.value = text;
        textArea.setAttribute("readonly", "readonly");
        textArea.style.position = "fixed";
        textArea.style.left = "-9999px";
        document.body.appendChild(textArea);
        textArea.focus();
        textArea.select();
        try {
            const success = document.execCommand("copy");
            if (!success) {
                throw new Error("浏览器未允许自动复制，请手动复制链接。");
            }
        } finally {
            document.body.removeChild(textArea);
        }
    }

    function getDistinctGrades(rows) {
        return Array.from(new Set((rows || []).map(item => item.gradeName).filter(Boolean)));
    }

    function fillEditGradeOptions(selectedGrade) {
        const select = document.getElementById("editGradeName");
        if (!select) {
            return;
        }
        let html = '<option value="">请选择年级</option>';
        getDistinctGrades(classOptions).forEach((grade) => {
            html += `<option value="${grade}">${grade}</option>`;
        });
        select.innerHTML = html;
        select.value = selectedGrade || "";
    }

    function fillEditClassOptions(selectedGrade, selectedClassId) {
        const select = document.getElementById("editClassId");
        if (!select) {
            return;
        }
        let html = '<option value="">请选择班级</option>';
        (classOptions || [])
            .filter(item => !selectedGrade || item.gradeName === selectedGrade)
            .forEach((item) => {
                html += `<option value="${item.classId}">${item.gradeName}${item.className}</option>`;
            });
        select.innerHTML = html;
        select.value = selectedClassId ? String(selectedClassId) : "";
    }

    function fillStudentOrderClassOptions(selectedClassId) {
        const select = document.getElementById("studentOrderClassId");
        if (!select) {
            return;
        }
        let html = '<option value="">请选择班级</option>';
        (classOptions || []).forEach((item) => {
            html += `<option value="${item.classId}">${item.gradeName}${item.className}</option>`;
        });
        select.innerHTML = html;
        select.value = selectedClassId ? String(selectedClassId) : "";
    }


    function fillLeaderboardFilters() {
        if (!isAdmin) {
            return;
        }
        const gradeSelect = document.getElementById("leaderboardGradeFilter");
        const classSelect = document.getElementById("leaderboardClassFilter");
        if (!gradeSelect || !classSelect) {
            return;
        }
        const grades = Array.from(new Set((classOptions || []).map(item => item.gradeName).filter(Boolean)));
        gradeSelect.innerHTML = '<option value="">全部年级</option>' + grades.map(item => `<option value="${item}">${item}</option>`).join("");
        const selectedGrade = gradeSelect.value || "";
        const filteredClasses = (classOptions || []).filter(item => !selectedGrade || item.gradeName === selectedGrade);
        const currentClassValue = classSelect.value || "";
        classSelect.innerHTML = '<option value="">全部班级</option>' + filteredClasses.map(item => `<option value="${item.classId}">${item.gradeName}${item.className}</option>`).join("");
        if (currentClassValue && filteredClasses.some(item => String(item.classId) == currentClassValue)) {
            classSelect.value = currentClassValue;
        }
    }

    function getSelectedLeaderboardClassLabel() {
        const classSelect = document.getElementById("leaderboardClassFilter");
        if (!classSelect || !classSelect.value) {
            return "";
        }
        const target = (classOptions || []).find(item => String(item.classId) === String(classSelect.value));
        return target ? `${target.gradeName || ""}${target.className || ""}` : "";
    }

    function updateUserEditFieldsByRole() {
        const roleCode = document.getElementById("editRoleCode");
        const gradeSelect = document.getElementById("editGradeName");
        const classSelect = document.getElementById("editClassId");
        if (!roleCode || !gradeSelect || !classSelect) {
            return;
        }
        const role = roleCode.value;
        const isTeacher = role === "TEACHER";
        const isGradeLeader = role === "GRADE_LEADER";
        gradeSelect.disabled = !(isTeacher || isGradeLeader);
        classSelect.disabled = !isTeacher;
        if (role === "STAFF") {
            gradeSelect.value = "";
            classSelect.value = "";
        } else if (role === "GRADE_LEADER") {
            classSelect.value = "";
        }
    }

    function buildImportStatus(result, typeLabel) {
        const lines = [
            `${result.preview ? typeLabel + "预校验完成" : typeLabel + "导入完成"}：总计 ${result.totalRows} 行，新增 ${result.createCount} 条，更新 ${result.updateCount} 条，跳过 ${result.skippedCount} 条，失败 ${result.errorCount} 条。`
        ];
        if (result.errors && result.errors.length) {
            const previews = result.errors.slice(0, 8).map(item => `第 ${item.rowNumber} 行：${item.reason}`);
            lines.push(previews.join("<br>"));
            if (result.errors.length > 8) {
                lines.push(`其余 ${result.errors.length - 8} 条错误请继续分批修正后导入。`);
            }
        }
        return lines.join("<br>");
    }

    function setProgress(barId, percent) {
        const bar = document.getElementById(barId);
        if (bar) {
            bar.style.width = `${Math.max(0, Math.min(100, percent))}%`;
        }
    }

    function updateExportFields() {
        const scope = document.getElementById("exportScope").value;
        document.getElementById("exportDateField").classList.toggle("hidden", scope !== "daily");
        document.getElementById("exportWeekField").classList.toggle("hidden", scope !== "weekly");
        document.getElementById("exportMonthField").classList.toggle("hidden", scope !== "monthly");
        const tips = {
            daily: "按所选日期导出当天打卡数据。",
            weekly: "按开学日期推算周次，请从下拉框选择要导出的周次。",
            monthly: "请从下拉框选择要导出的月份。",
            semester: "将导出本学期开学至今的全部打卡数据。"
        };
        setStatus("exportStatus", tips[scope], null);
    }

    function fillWeekOptions(weekCount) {
        const select = document.getElementById("exportWeekIndex");
        const count = Math.max(weekCount || 0, 1);
        const start = semesterStartDate ? new Date(`${semesterStartDate}T00:00:00`) : new Date();
        let html = '<option value="">请选择周次</option>';
        for (let i = 1; i <= count; i++) {
            const weekStart = new Date(start);
            weekStart.setDate(start.getDate() + (i - 1) * 7);
            const weekEnd = new Date(weekStart);
            weekEnd.setDate(weekStart.getDate() + 6);
            const range = `${weekStart.getFullYear()}-${String(weekStart.getMonth() + 1).padStart(2, "0")}-${String(weekStart.getDate()).padStart(2, "0")} ~ ${weekEnd.getFullYear()}-${String(weekEnd.getMonth() + 1).padStart(2, "0")}-${String(weekEnd.getDate()).padStart(2, "0")}`;
            html += `<option value="${i}">第 ${i} 周（${range}）</option>`;
        }
        select.innerHTML = html;
        if (count >= 1) {
            select.value = String(Math.max(1, weekCount || 1));
        }
    }

    function fillMonthOptions(startDateText) {
        const select = document.getElementById("exportMonth");
        let html = '<option value="">请选择月份</option>';
        if (!startDateText) {
            select.innerHTML = html;
            return;
        }
        const startDate = new Date(`${startDateText}T00:00:00`);
        const now = new Date();
        let year = startDate.getFullYear();
        let month = startDate.getMonth() + 1;
        const endYear = now.getFullYear();
        const endMonth = now.getMonth() + 1;
        let latestValue = "";
        while (year < endYear || (year === endYear && month <= endMonth)) {
            const value = `${year}-${String(month).padStart(2, "0")}`;
            const monthIndex = ((year - startDate.getFullYear()) * 12) + month - startDate.getMonth();
            html += `<option value="${value}">${year}年${String(month).padStart(2, "0")}月（开学第${monthIndex}月）</option>`;
            latestValue = value;
            month += 1;
            if (month > 12) {
                month = 1;
                year += 1;
            }
        }
        select.innerHTML = html;
        if (latestValue) {
            select.value = latestValue;
        }
    }

    async function api(url, options) {
        const config = Object.assign({}, options || {});
        config.headers = Object.assign({}, config.headers || {}, {
            "Authorization": `Bearer ${auth.token}`
        });
        const response = await fetch(url, config);
        const contentType = response.headers.get("content-type") || "";
        if (contentType.includes("application/json")) {
            const json = await response.json();
            if (!response.ok || json.code !== 200) {
                throw new Error(json.message || "请求失败");
            }
            return json.data;
        }
        if (!response.ok) {
            throw new Error("请求失败");
        }
        return response;
    }

    function uploadWithProgress(url, file, progressBarId) {
        return new Promise((resolve, reject) => {
            const xhr = new XMLHttpRequest();
            const formData = new FormData();
            formData.append("file", file);
            xhr.open("POST", url, true);
            xhr.setRequestHeader("Authorization", `Bearer ${auth.token}`);
            xhr.upload.onprogress = (event) => {
                if (event.lengthComputable) {
                    setProgress(progressBarId, Math.round((event.loaded / event.total) * 100));
                }
            };
            xhr.onload = () => {
                setProgress(progressBarId, 100);
                try {
                    const json = JSON.parse(xhr.responseText || "{}");
                    if (xhr.status !== 200 || json.code !== 200) {
                        reject(new Error(json.message || "上传失败"));
                        return;
                    }
                    resolve(json.data);
                } catch (error) {
                    reject(new Error("响应解析失败"));
                }
            };
            xhr.onerror = () => reject(new Error("上传失败"));
            xhr.send(formData);
        });
    }

    async function loadSemester() {
        const data = await api("/api/admin/semester-start");
        semesterStartDate = data.semesterStartDate || "";
        currentWeek = data.currentWeek || 0;
        document.getElementById("metricWeek").textContent = data.currentWeek === 0 ? "未开学" : `第 ${data.currentWeek} 周`;
        document.getElementById("metricSemesterDate").textContent = data.semesterStartDate || "--";
        const semesterDate = document.getElementById("semesterDate");
        if (semesterDate) {
            semesterDate.value = data.semesterStartDate || "";
        }
        fillWeekOptions(currentWeek);
        fillMonthOptions(semesterStartDate);
        setStatus("semesterStatus", `当前开学日期：${data.semesterStartDate || "--"}，当前周次：${data.currentWeek === 0 ? "未开学" : "第 " + data.currentWeek + " 周"}`, "success");
    }

    async function loadDailyQuotes() {
        if (!isAdmin) {
            return;
        }
        const data = await api("/api/admin/daily-quotes");
        const morningTextArea = document.getElementById("morningQuotesText");
        const eveningTextArea = document.getElementById("eveningQuotesText");
        if (morningTextArea) {
            morningTextArea.value = data.morningQuotesText || "";
        }
        if (eveningTextArea) {
            eveningTextArea.value = data.eveningQuotesText || "";
        }
        syncQuoteStartOptions("morning", data.morningStartIndex || 1);
        syncQuoteStartOptions("evening", data.eveningStartIndex || 1);
        renderQuotePreview("morning", data.morningTodayQuote || "未设置", data.morningTodayIndex || 1);
        renderQuotePreview("evening", data.eveningTodayQuote || "未设置", data.eveningTodayIndex || 1);
        setStatus(
            "dailyQuotesStatus",
            `早上共 ${data.morningQuoteCount || 0} 条，今天显示第 ${data.morningTodayIndex || 1} 句；晚上共 ${data.eveningQuoteCount || 0} 条，今天显示第 ${data.eveningTodayIndex || 1} 句。`,
            "success"
        );
    }

    async function loadCheckinDebugSetting() {
        if (!isAdmin) {
            return;
        }
        const data = await api("/api/admin/checkin-debug");
        const select = document.getElementById("checkinDebugEnabled");
        if (select) {
            select.value = data.enabled ? "1" : "0";
        }
        setStatus("checkinDebugStatus", data.enabled ? "当前为开启状态：服务器已放开打卡时间限制。" : "当前为关闭状态：服务器严格按指定时间限制打卡。", data.enabled ? "success" : null);
    }

    async function loadAccessBaseUrl() {
        if (!isAdmin) {
            return;
        }
        const data = await api("/api/admin/access-base-url");
        accessBaseUrl = data.accessBaseUrl || location.origin;
    }

    function getGradeNamesForRules() {
        const grades = Array.from(new Set((classOptions || []).map(item => item.gradeName).filter(Boolean)));
        return grades.length ? grades : ["七年级", "八年级", "九年级"];
    }

    function renderEveningCheckinRules(rows) {
        const body = document.getElementById("eveningCheckinRuleBody");
        if (!body) {
            return;
        }
        const mapped = new Map((rows || []).map(item => [item.gradeName, item]));
        body.innerHTML = getGradeNamesForRules().map((gradeName) => {
            const row = mapped.get(gradeName) || {
                gradeName,
                morningStart: "06:00",
                morningEnd: "07:10",
                normalStart: "21:30",
                normalEnd: "21:50",
                returnStart: "19:00",
                returnEnd: "19:20"
            };
            return `
                <tr>
                    <td>${gradeName}</td>
                    <td><input class="input evening-rule-input" data-grade="${gradeName}" data-field="morningStart" type="time" value="${row.morningStart || "06:00"}"></td>
                    <td><input class="input evening-rule-input" data-grade="${gradeName}" data-field="morningEnd" type="time" value="${row.morningEnd || "07:10"}"></td>
                    <td><input class="input evening-rule-input" data-grade="${gradeName}" data-field="normalStart" type="time" value="${row.normalStart || "21:30"}"></td>
                    <td><input class="input evening-rule-input" data-grade="${gradeName}" data-field="normalEnd" type="time" value="${row.normalEnd || "21:50"}"></td>
                    <td><input class="input evening-rule-input" data-grade="${gradeName}" data-field="returnStart" type="time" value="${row.returnStart || "19:00"}"></td>
                    <td><input class="input evening-rule-input" data-grade="${gradeName}" data-field="returnEnd" type="time" value="${row.returnEnd || "19:20"}"></td>
                </tr>
            `;
        }).join("");
    }

    async function loadEveningCheckinSettings() {
        if (!isAdmin) {
            return;
        }
        const data = await api("/api/admin/evening-checkin-settings");
        document.getElementById("returnDate").value = data.overrideReturnDate || data.suggestedReturnDate || "";
        const returnDateHint = document.getElementById("returnDateHint");
        if (returnDateHint) {
            returnDateHint.textContent = `本周默认返校日：${data.suggestedReturnDate || "--"}；当前生效返校日：${data.effectiveReturnDate || "--"}`;
        }
        renderEveningCheckinRules(data.rules || []);
        setStatus("eveningCheckinStatus", `分年级打卡规则已加载。手动返校日期：${data.overrideReturnDate || "未单独指定"}。`, "success");
    }

    async function saveEveningCheckinSettings() {
        const rows = Array.from(document.querySelectorAll(".evening-rule-input")).reduce((acc, input) => {
            const grade = input.dataset.grade;
            if (!acc[grade]) {
                acc[grade] = { gradeName: grade };
            }
            acc[grade][input.dataset.field] = input.value;
            return acc;
        }, {});
        const payload = {
            overrideReturnDate: document.getElementById("returnDate").value || "",
            rules: Object.values(rows)
        };
        setStatus("eveningCheckinStatus", "正在保存分年级打卡规则...", null);
        await api("/api/admin/evening-checkin-settings", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });
        await loadEveningCheckinSettings();
    }

    async function loadRankSettings() {
        if (!isAdmin) {
            return;
        }
        const data = await api("/api/admin/rank-settings");
        [
            "bronzeCount", "bronze2Count", "bronze1Count",
            "silverCount", "silver2Count", "silver1Count",
            "goldCount", "gold3Count", "gold2Count", "gold1Count",
            "platinumCount", "platinum3Count", "platinum2Count", "platinum1Count",
            "diamondCount", "diamond4Count", "diamond3Count", "diamond2Count", "diamond1Count",
            "starryCount", "starry4Count", "starry3Count", "starry2Count", "starry1Count",
            "kingCount", "legendCount", "gloryCount",
            "gloryPercent", "legendPercent", "kingPercent", "starryPercent", "diamondPercent", "platinumPercent", "goldPercent", "silverPercent", "bronzePercent"
        ].forEach((id) => {
            const element = document.getElementById(id);
            if (element) {
                element.value = data[id] == null ? "" : data[id];
            }
        });
        document.getElementById("rankMode").value = data.mode || "COUNT";
        updateRankModeView();
        setStatus("rankSettingStatus", `当前规则模式：${data.mode === "PERCENT" ? "按人数百分比" : "按累计次数"}。`, "success");
    }

    function updateRankModeView() {
        const mode = document.getElementById("rankMode").value;
        const countDisabled = mode !== "COUNT";
        const percentDisabled = mode !== "PERCENT";
        [
            "bronzeCount", "bronze2Count", "bronze1Count",
            "silverCount", "silver2Count", "silver1Count",
            "goldCount", "gold3Count", "gold2Count", "gold1Count",
            "platinumCount", "platinum3Count", "platinum2Count", "platinum1Count",
            "diamondCount", "diamond4Count", "diamond3Count", "diamond2Count", "diamond1Count",
            "starryCount", "starry4Count", "starry3Count", "starry2Count", "starry1Count",
            "kingCount", "legendCount", "gloryCount"
        ].forEach((id) => {
            const element = document.getElementById(id);
            if (element) {
                element.disabled = countDisabled;
            }
        });
        ["gloryPercent", "legendPercent", "kingPercent", "starryPercent", "diamondPercent", "platinumPercent", "goldPercent", "silverPercent", "bronzePercent"].forEach((id) => {
            const element = document.getElementById(id);
            if (element) {
                element.disabled = percentDisabled;
            }
        });
    }

    async function saveRankSettings() {
        const payload = {
            mode: document.getElementById("rankMode").value,
            bronzeCount: Number(document.getElementById("bronzeCount").value || 0),
            bronze2Count: Number(document.getElementById("bronze2Count").value || 0),
            bronze1Count: Number(document.getElementById("bronze1Count").value || 0),
            silverCount: Number(document.getElementById("silverCount").value || 0),
            silver2Count: Number(document.getElementById("silver2Count").value || 0),
            silver1Count: Number(document.getElementById("silver1Count").value || 0),
            goldCount: Number(document.getElementById("goldCount").value || 0),
            gold3Count: Number(document.getElementById("gold3Count").value || 0),
            gold2Count: Number(document.getElementById("gold2Count").value || 0),
            gold1Count: Number(document.getElementById("gold1Count").value || 0),
            platinumCount: Number(document.getElementById("platinumCount").value || 0),
            platinum3Count: Number(document.getElementById("platinum3Count").value || 0),
            platinum2Count: Number(document.getElementById("platinum2Count").value || 0),
            platinum1Count: Number(document.getElementById("platinum1Count").value || 0),
            diamondCount: Number(document.getElementById("diamondCount").value || 0),
            diamond4Count: Number(document.getElementById("diamond4Count").value || 0),
            diamond3Count: Number(document.getElementById("diamond3Count").value || 0),
            diamond2Count: Number(document.getElementById("diamond2Count").value || 0),
            diamond1Count: Number(document.getElementById("diamond1Count").value || 0),
            starryCount: Number(document.getElementById("starryCount").value || 0),
            starry4Count: Number(document.getElementById("starry4Count").value || 0),
            starry3Count: Number(document.getElementById("starry3Count").value || 0),
            starry2Count: Number(document.getElementById("starry2Count").value || 0),
            starry1Count: Number(document.getElementById("starry1Count").value || 0),
            kingCount: Number(document.getElementById("kingCount").value || 0),
            legendCount: Number(document.getElementById("legendCount").value || 0),
            gloryCount: Number(document.getElementById("gloryCount").value || 0),
            gloryPercent: Number(document.getElementById("gloryPercent").value || 0),
            legendPercent: Number(document.getElementById("legendPercent").value || 0),
            kingPercent: Number(document.getElementById("kingPercent").value || 0),
            starryPercent: Number(document.getElementById("starryPercent").value || 0),
            diamondPercent: Number(document.getElementById("diamondPercent").value || 0),
            platinumPercent: Number(document.getElementById("platinumPercent").value || 0),
            goldPercent: Number(document.getElementById("goldPercent").value || 0),
            silverPercent: Number(document.getElementById("silverPercent").value || 0),
            bronzePercent: Number(document.getElementById("bronzePercent").value || 0)
        };
        setStatus("rankSettingStatus", "正在保存段位规则...", null);
        await api("/api/admin/rank-settings", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });
        await loadRankSettings();
    }

    async function loadClassLinks() {
        if (!isAdmin) {
            return;
        }
        const rows = await api("/api/admin/classes");
        classOptions = rows || [];
        fillEditGradeOptions(document.getElementById("editGradeName") ? document.getElementById("editGradeName").value : "");
        fillEditClassOptions(document.getElementById("editGradeName") ? document.getElementById("editGradeName").value : "", document.getElementById("editClassId") ? document.getElementById("editClassId").value : "");
        document.getElementById("metricClassCount").textContent = rows.length;
        document.getElementById("metricStudentCount").textContent = rows.reduce((sum, item) => sum + (item.studentCount || 0), 0);
        const body = document.getElementById("classLinksBody");
        body.innerHTML = rows.map(item => {
            const link = `${accessBaseUrl}/checkin.html?code=${encodeURIComponent(item.uniqueCode)}`;
            return `
                <tr>
                    <td>${item.gradeName}${item.className}</td>
                    <td>${item.teacherName || "-"}</td>
                    <td>${item.teacherUsername || "-"}</td>
                    <td>${item.studentCount || 0}</td>
                    <td>${item.uniqueCode}</td>
                    <td><button class="ghost-button copy-link" data-link="${link}">复制链接</button></td>
                </tr>
            `;
        }).join("");
        body.querySelectorAll(".copy-link").forEach(button => {
            button.addEventListener("click", async () => {
                try {
                    await copyText(button.dataset.link || "");
                    button.textContent = "已复制";
                    window.setTimeout(() => {
                        button.textContent = "复制链接";
                    }, 1200);
                } catch (error) {
                    setStatus("linksStatus", error.message || "复制失败，请手动复制链接。", "error");
                }
            });
        });
        fillStudentOrderClassOptions(document.getElementById("studentOrderClassId") ? document.getElementById("studentOrderClassId").value : "");
    }

        async function loadStudentOrderBindings() {
        const classId = document.getElementById("studentOrderClassId").value;
        if (!classId) {
            setStatus("studentOrderStatus", "请先选择班级。", "error");
            return;
        }
        setStatus("studentOrderStatus", "正在加载学生序号...", null);
        const rows = await api(`/api/admin/student-order-bindings?classId=${encodeURIComponent(classId)}`);
        const body = document.getElementById("studentOrderTableBody");
        if (!rows.length) {
            body.innerHTML = '<tr><td colspan="6" class="muted">当前班级没有可用学生数据。</td></tr>';
            setStatus("studentOrderStatus", "当前班级没有可用学生数据。", "error");
            return;
        }
        body.innerHTML = rows.map(item => `
            <tr>
                <td>${item.studentNo || "-"}</td>
                <td>${item.studentName || "-"}</td>
                <td>${item.defaultOrderNo == null ? "-" : item.defaultOrderNo}</td>
                <td>${item.effectiveOrderNo == null ? "-" : item.effectiveOrderNo}</td>
                <td><input class="input student-order-input" type="number" min="0" value="${item.effectiveOrderNo == null ? "" : item.effectiveOrderNo}" data-student-id="${item.studentId}" data-default-order="${item.defaultOrderNo == null ? "" : item.defaultOrderNo}"></td>
                <td><button class="ghost-button save-student-order" data-student-id="${item.studentId}">保存</button></td>
            </tr>
        `).join("");
        body.querySelectorAll(".save-student-order").forEach((button) => {
            button.addEventListener("click", async () => {
                const input = body.querySelector(`.student-order-input[data-student-id="${button.dataset.studentId}"]`);
                const rawValue = input ? input.value.trim() : "";
                const payload = {
                    studentId: Number(button.dataset.studentId),
                    displayOrderNo: rawValue === "" ? null : Number(rawValue)
                };
                const targetOrderNo = rawValue === "" ? (input.dataset.defaultOrder ? Number(input.dataset.defaultOrder) : null) : Number(rawValue);
                const duplicateInput = Array.from(body.querySelectorAll(".student-order-input")).find((item) => {
                    if (item.dataset.studentId === button.dataset.studentId) {
                        return false;
                    }
                    const currentValue = item.value.trim();
                    const effectiveOrder = currentValue === "" ? (item.dataset.defaultOrder ? Number(item.dataset.defaultOrder) : null) : Number(currentValue);
                    return effectiveOrder != null && targetOrderNo != null && effectiveOrder === targetOrderNo;
                });
                if (duplicateInput) {
                    setStatus("studentOrderStatus", `序号 ${targetOrderNo} 已在当前列表中重复，请调整后再保存。`, "error");
                    return;
                }
                try {
                    await api("/api/admin/student-order-bindings", {
                        method: "POST",
                        headers: { "Content-Type": "application/json" },
                        body: JSON.stringify(payload)
                    });
                    setStatus("studentOrderStatus", "学生打卡序号已保存。", "success");
                    await loadStudentOrderBindings();
                } catch (error) {
                    setStatus("studentOrderStatus", error.message, "error");
                }
            });
        });
        setStatus("studentOrderStatus", "已加载学生序号；输入框默认显示当前生效序号，保存时会校验重复。清空后保存即可恢复按学号后两位自动取号。", "success");
    }

    async function loadOverviewStats() {
        const data = await api("/api/data/overview");
        document.getElementById("metricClassCount").textContent = data.classCount == null ? "--" : data.classCount;
        document.getElementById("metricStudentCount").textContent = data.studentCount == null ? "--" : data.studentCount;
        const scopeCard = document.getElementById("overviewScopeCard");
        const scopeText = document.getElementById("overviewScopeText");
        if (scopeCard && scopeText) {
            scopeCard.classList.remove("hidden");
            if (auth.roleCode === "TEACHER") {
                scopeText.textContent = `当前账号绑定班级：${data.primaryClassLabel || "--"}，可查看并修正本班打卡数据。`;
            } else if (auth.roleCode === "GRADE_LEADER") {
                scopeText.textContent = `当前账号可查看并修正 ${auth.gradeName || "本年级"} 数据，共 ${data.classCount || 0} 个班，${data.studentCount || 0} 名学生。`;
            } else if (auth.roleCode === "STAFF") {
                scopeText.textContent = `当前账号可查看并修正全校数据，共 ${data.classCount || 0} 个班，${data.studentCount || 0} 名学生。`;
            } else {
                scopeText.textContent = `当前账号可查看并修正全校数据，共 ${data.classCount || 0} 个班，${data.studentCount || 0} 名学生。`;
            }
        }
    }

    function fillCorrectionClassOptions() {
        const select = document.getElementById("correctionClassId");
        if (!select) {
            return;
        }
        let html = '<option value="">请选择班级</option>';
        (classOptions || []).forEach((item) => {
            html += `<option value="${item.classId}">${item.gradeName}${item.className}</option>`;
        });
        select.innerHTML = html;
        if (auth.roleCode === "TEACHER" && classOptions.length === 1) {
            select.value = String(classOptions[0].classId);
            select.disabled = true;
        } else {
            select.disabled = false;
        }
    }

    async function loadCorrectionClasses() {
        const rows = await api("/api/data/classes");
        classOptions = rows || [];
        fillCorrectionClassOptions();
    }

    async function loadCorrectionRows() {
        const date = document.getElementById("correctionDate").value;
        const timeSlot = document.getElementById("correctionSlot").value;
        const classId = document.getElementById("correctionClassId").value;
        if (!date) {
            setStatus("correctionStatus", "请选择日期。", "error");
            return;
        }
        if (!classId && auth.roleCode !== "TEACHER") {
            setStatus("correctionStatus", "请选择要修正的班级。", "error");
            return;
        }
        const rows = await api(`/api/data/correction/records?date=${encodeURIComponent(date)}&timeSlot=${encodeURIComponent(timeSlot)}${classId ? `&classId=${encodeURIComponent(classId)}` : ""}`);
        const body = document.getElementById("correctionTableBody");
        if (!rows.length) {
            body.innerHTML = '<tr><td colspan="6" class="muted">当前查询范围没有学生数据。</td></tr>';
            setStatus("correctionStatus", "未查到可修正的学生数据。", null);
            return;
        }
        body.innerHTML = rows.map((item) => `
            <tr>
                <td>${item.gradeName || ""}${item.className || ""}</td>
                <td>${item.studentNo || "-"}</td>
                <td>${item.studentName || "-"}</td>
                <td>${Number(item.checkedIn) === 1 ? "已打卡" : "未打卡"}</td>
                <td>${item.checkinTime || "-"}</td>
                <td>
                    ${Number(item.checkedIn) === 1
                        ? `<button class="ghost-button correction-action" data-action="revoke" data-student-id="${item.studentId}">撤销打卡</button>`
                        : `<button class="button correction-action" data-action="checkin" data-student-id="${item.studentId}">补录打卡</button>`}
                </td>
            </tr>
        `).join("");
        body.querySelectorAll(".correction-action").forEach((button) => {
            button.addEventListener("click", async () => {
                const action = button.dataset.action;
                if (action === "checkin") {
                    openCorrectionCheckinModal(rows.find((item) => Number(item.studentId) === Number(button.dataset.studentId)), "checkin");
                    return;
                }
                openCorrectionCheckinModal(rows.find((item) => Number(item.studentId) === Number(button.dataset.studentId)), "revoke");
            });
        });
        setStatus("correctionStatus", `已加载 ${rows.length} 条学生记录，可直接补卡或撤销。`, "success");
    }

    function syncCorrectionTimeDefault() {
        const slot = document.getElementById("correctionSlot").value;
        const timeInput = document.getElementById("correctionModalTime");
        if (!timeInput) {
            return;
        }
        if (document.getElementById("correctionModalAction").value === "checkin") {
            timeInput.value = "";
            timeInput.placeholder = slot === "MORNING" ? "请选择早上打卡时间" : "请选择晚上打卡时间";
        }
    }

    function openCorrectionCheckinModal(item, action) {
        if (!item) {
            return;
        }
        const modalAction = action === "revoke" ? "revoke" : "checkin";
        document.getElementById("correctionModalStudentId").value = item.studentId || "";
        document.getElementById("correctionModalAction").value = modalAction;
        document.getElementById("correctionModalTitle").textContent = modalAction === "checkin" ? "补录打卡" : "撤销打卡";
        document.getElementById("correctionModalStudentText").textContent = modalAction === "checkin"
            ? `为 ${item.gradeName || ""}${item.className || ""} ${item.studentName || ""} 选择补录时间后确认。`
            : `确认撤销 ${item.gradeName || ""}${item.className || ""} ${item.studentName || ""} 的本次打卡记录。`;
        document.getElementById("confirmCorrectionCheckinBtn").textContent = modalAction === "checkin" ? "确认补录" : "确认撤销";
        document.getElementById("correctionModalTime").disabled = modalAction !== "checkin";
        syncCorrectionTimeDefault();
        setStatus("correctionModalStatus", modalAction === "checkin"
            ? "补录时间会写入打卡记录，并影响同次数时的榜单排序。"
            : "撤销后该条打卡记录将被删除，并立即影响榜单与统计。", null);
        openModal(correctionCheckinModal);
    }

    async function confirmCorrectionCheckin() {
        const studentId = Number(document.getElementById("correctionModalStudentId").value);
        const date = document.getElementById("correctionDate").value;
        const timeSlot = document.getElementById("correctionSlot").value;
        const checkinTime = (document.getElementById("correctionModalTime").value || "").trim();
        const action = document.getElementById("correctionModalAction").value || "checkin";
        if (!studentId) {
            setStatus("correctionModalStatus", "未识别要操作的学生。", "error");
            return;
        }
        if (action === "checkin" && !checkinTime) {
            setStatus("correctionModalStatus", "请选择补录时间。", "error");
            return;
        }
        setStatus("correctionModalStatus", action === "checkin" ? "正在补录打卡..." : "正在撤销打卡...", null);
        await api(action === "checkin" ? "/api/data/correction/check-in" : "/api/data/correction/revoke", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                date,
                timeSlot,
                checkinTime: action === "checkin" ? checkinTime : null,
                studentId
            })
        });
        closeModal(correctionCheckinModal);
        await loadCorrectionRows();
        setStatus("correctionStatus", action === "checkin" ? "补卡成功，列表已刷新。" : "撤销成功，列表已刷新。", "success");
    }

    async function loadUserAccounts() {
        if (!isAdmin) {
            return;
        }
        const rows = await api("/api/admin/users");
        const body = document.getElementById("accountsTableBody");
        if (!rows.length) {
            body.innerHTML = '<tr><td colspan="8" class="muted">暂无账号数据。</td></tr>';
            return;
        }
        body.innerHTML = rows.map((item) => `
            <tr>
                <td>${item.realName || "-"}</td>
                <td>${item.username || "-"}</td>
                <td>${roleLabelMap[item.roleCode] || item.roleCode}</td>
                <td>${item.gradeName || "-"}</td>
                <td>${item.className || "-"}</td>
                <td>${item.enabled === 1 ? "启用" : "停用"}</td>
                <td>${item.mustChangePassword === 1 ? "是" : "否"}</td>
                <td>
                    <button class="ghost-button edit-user" data-user-id="${item.userId}">编辑</button>
                    <button class="ghost-button reset-password" data-user-id="${item.userId}">重置为 dlzx</button>
                </td>
            </tr>
        `).join("");
        body.querySelectorAll(".edit-user").forEach(button => {
            button.addEventListener("click", () => {
                const item = rows.find(row => Number(row.userId) === Number(button.dataset.userId));
                if (!item) {
                    return;
                }
                document.getElementById("editUserId").value = item.userId || "";
                document.getElementById("editRealName").value = item.realName || "";
                document.getElementById("editRoleCode").value = item.roleCode || "TEACHER";
                document.getElementById("editEnabled").value = String(item.enabled == null ? 1 : item.enabled);
                fillEditGradeOptions(item.gradeName || "");
                fillEditClassOptions(item.gradeName || "", item.classId || "");
                updateUserEditFieldsByRole();
                setStatus("userEditStatus", `正在编辑账号：${item.username || "--"}。修改后点击“保存账号信息”。`, null);
                openModal(userEditModal);
            });
        });
        body.querySelectorAll(".reset-password").forEach(button => {
            button.addEventListener("click", async () => {
                const confirmed = window.confirm("确认将该账号密码重置为 dlzx，并要求其下次登录强制修改吗？");
                if (!confirmed) {
                    return;
                }
                try {
                    await api("/api/admin/users/reset-password", {
                        method: "POST",
                        headers: { "Content-Type": "application/json" },
                        body: JSON.stringify({ userId: Number(button.dataset.userId) })
                    });
                    await loadUserAccounts();
                    alert("密码已重置为 dlzx");
                } catch (error) {
                    alert(error.message || "重置失败");
                }
            });
        });
    }

    async function loadLeaderboard() {
        const date = document.getElementById("leaderboardDate").value;
        const timeSlot = document.getElementById("leaderboardSlot").value;
        leaderboardRowsCache = await api(`/api/data/leaderboard?date=${encodeURIComponent(date)}&timeSlot=${encodeURIComponent(timeSlot)}`);
        if (!isAdmin) {
            renderLeaderboardRows();
            hideClassLeaderboardDetail();
            return;
        }
        studentQualityRowsCache = await api('/api/data/leaderboard/student-quality');
        fillLeaderboardFilters();
        studentQualityPage = 1;
        renderLeaderboardRows();
        renderStudentQualityRows();
        if (currentLeaderboardClassId) {
            await showClassLeaderboardDetail(currentLeaderboardClassId);
        }
    }

    function renderLeaderboardRows() {
        const body = document.getElementById("leaderboardTableBody");
        if (!body) {
            return;
        }
        const gradeFilter = isAdmin && document.getElementById("leaderboardGradeFilter") ? document.getElementById("leaderboardGradeFilter").value : "";
        const classFilter = isAdmin && document.getElementById("leaderboardClassFilter") ? document.getElementById("leaderboardClassFilter").value : "";
        const rows = (leaderboardRowsCache || []).filter((item) => {
            if (gradeFilter && item.gradeName !== gradeFilter) {
                return false;
            }
            if (classFilter && String(item.classId) !== String(classFilter)) {
                return false;
            }
            return true;
        });
        if (!rows.length) {
            body.innerHTML = '<tr><td colspan="5" class="muted">没有查询到数据。</td></tr>';
            if (!classFilter) {
                hideClassLeaderboardDetail();
            }
            return;
        }
        body.innerHTML = rows.map((item, index) => `
            <tr class="clickable-row" data-class-id="${item.classId}" data-class-label="${item.gradeName || ""}${item.className || ""}">
                <td>${index + 1}</td>
                <td><button class="table-link-button" type="button">${item.gradeName}${item.className}</button></td>
                <td>${item.totalStudents}</td>
                <td>${item.checkedStudents}</td>
                <td>${item.attendanceRate}%</td>
            </tr>
        `).join("");
        body.querySelectorAll("tr[data-class-id]").forEach((row) => {
            row.addEventListener("click", () => {
                if (isAdmin && document.getElementById("leaderboardClassFilter")) {
                    document.getElementById("leaderboardClassFilter").value = row.dataset.classId;
                }
                showClassLeaderboardDetail(Number(row.dataset.classId), row.dataset.classLabel || "");
            });
        });
    }

    function renderStudentQualityRows() {
        if (!isAdmin) {
            return;
        }
        const body = document.getElementById("studentQualityTableBody");
        const summary = document.getElementById("studentQualitySummary");
        const pageInfo = document.getElementById("studentQualityPageInfo");
        const prevBtn = document.getElementById("studentQualityPrevBtn");
        const nextBtn = document.getElementById("studentQualityNextBtn");
        if (!body) {
            return;
        }
        const gradeFilter = document.getElementById("leaderboardGradeFilter") ? document.getElementById("leaderboardGradeFilter").value : "";
        const classFilter = document.getElementById("leaderboardClassFilter") ? document.getElementById("leaderboardClassFilter").value : "";
        const rows = (studentQualityRowsCache || []).filter((item) => {
            if (gradeFilter && item.gradeName !== gradeFilter) {
                return false;
            }
            if (classFilter && String((classOptions || []).find(option => option.gradeName === item.gradeName && option.className === item.className)?.classId || "") !== String(classFilter)) {
                return false;
            }
            return true;
        });
        const totalPages = Math.max(1, Math.ceil(rows.length / studentQualityPageSize));
        if (studentQualityPage > totalPages) {
            studentQualityPage = totalPages;
        }
        if (studentQualityPage < 1) {
            studentQualityPage = 1;
        }
        const startIndex = (studentQualityPage - 1) * studentQualityPageSize;
        const pageRows = rows.slice(startIndex, startIndex + studentQualityPageSize);
        if (summary) {
            summary.textContent = rows.length ? `共 ${rows.length} 名学生，当前显示第 ${startIndex + 1}-${startIndex + pageRows.length} 名。` : '当前筛选范围内没有学生质量分数据。';
        }
        if (pageInfo) {
            pageInfo.textContent = `第 ${studentQualityPage} / ${totalPages} 页`;
        }
        if (prevBtn) {
            prevBtn.disabled = studentQualityPage <= 1;
        }
        if (nextBtn) {
            nextBtn.disabled = studentQualityPage >= totalPages;
        }
        if (!rows.length) {
            body.innerHTML = '<tr><td colspan="7" class="muted">当前筛选范围内没有学生质量分数据。</td></tr>';
            return;
        }
        body.innerHTML = pageRows.map((item, index) => `
            <tr>
                <td>${startIndex + index + 1}</td>
                <td>${item.gradeName || ""}${item.className || ""}</td>
                <td>${item.studentNo || "-"}</td>
                <td>${item.studentName || "-"}</td>
                <td>${item.totalCheckinCount || 0}</td>
                <td>${formatQualityScore(item.qualityScore)}</td>
                <td>${item.rankTitle || "-"}</td>
            </tr>
        `).join("");
    }

    function hideClassLeaderboardDetail() {
        currentLeaderboardClassId = null;
        const card = document.getElementById("classLeaderboardDetailCard");
        const body = document.getElementById("classLeaderboardDetailBody");
        const title = document.getElementById("classLeaderboardDetailTitle");
        const desc = document.getElementById("classLeaderboardDetailDesc");
        if (card) {
            card.classList.add("hidden");
        }
        if (title) {
            title.textContent = "班级学生榜";
        }
        if (desc) {
            desc.textContent = "点击上方班级后查看该班学生详细榜单。";
        }
        if (body) {
            body.innerHTML = '<tr><td colspan="6" class="muted">点击上方班级后查看该班学生详细榜单。</td></tr>';
        }
    }

    async function showClassLeaderboardDetail(classId, classLabel) {
        if (!isAdmin || !classId) {
            return;
        }
        currentLeaderboardClassId = classId;
        const card = document.getElementById("classLeaderboardDetailCard");
        const body = document.getElementById("classLeaderboardDetailBody");
        const title = document.getElementById("classLeaderboardDetailTitle");
        const desc = document.getElementById("classLeaderboardDetailDesc");
        if (card) {
            card.classList.remove("hidden");
        }
        if (title) {
            title.textContent = `${classLabel || "该班"}学生榜`;
        }
        if (desc) {
            desc.textContent = "正在加载该班学生榜单...";
        }
        if (body) {
            body.innerHTML = '<tr><td colspan="6" class="muted">正在加载该班学生榜单...</td></tr>';
        }
        const rows = await api(`/api/data/leaderboard/student-quality?classId=${encodeURIComponent(classId)}`);
        if (title && classLabel) {
            title.textContent = `${classLabel}学生榜`;
        }
        if (!rows.length) {
            if (desc) {
                desc.textContent = "该班当前没有学生质量分数据。";
            }
            if (body) {
                body.innerHTML = '<tr><td colspan="6" class="muted">该班当前没有学生质量分数据。</td></tr>';
            }
            return;
        }
        if (desc) {
            desc.textContent = `共 ${rows.length} 名学生，按累计次数和质量分排序。`;
        }
        if (body) {
            body.innerHTML = rows.map((item, index) => `
                <tr>
                    <td>${index + 1}</td>
                    <td>${item.studentNo || "-"}</td>
                    <td>${item.studentName || "-"}</td>
                    <td>${item.totalCheckinCount || 0}</td>
                    <td>${formatQualityScore(item.qualityScore)}</td>
                    <td>${item.rankTitle || "-"}</td>
                </tr>
            `).join("");
        }
        if (card) {
            card.scrollIntoView({ behavior: "smooth", block: "start" });
        }
    }

    async function exportReport() {
        const scope = document.getElementById("exportScope").value;
        const date = document.getElementById("exportDate").value;
        const weekIndex = document.getElementById("exportWeekIndex").value;
        const month = document.getElementById("exportMonth").value;
        let query = `scope=${encodeURIComponent(scope)}`;
        if (scope === "daily") {
            query += `&date=${encodeURIComponent(date)}`;
        } else if (scope === "weekly") {
            if (!weekIndex) {
                setStatus("exportStatus", "请输入要导出的第几周。", "error");
                return;
            }
            query += `&weekIndex=${encodeURIComponent(weekIndex)}`;
        } else if (scope === "monthly") {
            if (!month) {
                setStatus("exportStatus", "请选择要导出的月份。", "error");
                return;
            }
            query += `&month=${encodeURIComponent(month)}`;
        }
        setStatus("exportStatus", "正在生成 Excel，请稍候...", null);
        const response = await api(`/api/data/export?${query}`);
        const blob = await response.blob();
        const disposition = response.headers.get("Content-Disposition") || "";
        const matched = disposition.match(/filename\*=utf-8''(.+)/i);
        const fileName = matched ? decodeURIComponent(matched[1]) : "checkin-report.xlsx";
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement("a");
        anchor.href = url;
        anchor.download = fileName;
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        URL.revokeObjectURL(url);
        setStatus("exportStatus", `导出成功：${fileName}`, "success");
    }

    async function exportClassLinks() {
        setStatus("linksStatus", "正在生成班级链接表，请稍候...", null);
        const response = await api(`/api/admin/classes/export?baseUrl=${encodeURIComponent(accessBaseUrl)}`);
        const blob = await response.blob();
        const disposition = response.headers.get("Content-Disposition") || "";
        const matched = disposition.match(/filename\*=utf-8''(.+)/i);
        const fileName = matched ? decodeURIComponent(matched[1]) : "班级免登录打卡链接表.xlsx";
        const blobUrl = URL.createObjectURL(blob);
        const anchor = document.createElement("a");
        anchor.href = blobUrl;
        anchor.download = fileName;
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        URL.revokeObjectURL(blobUrl);
        setStatus("linksStatus", `导出成功：${fileName}`, "success");
    }

    async function downloadClassLinkZip() {
        setStatus("linksStatus", "正在生成快捷方式压缩包，请稍候...", null);
        const response = await api(`/api/admin/classes/zip?baseUrl=${encodeURIComponent(accessBaseUrl)}`);
        const blob = await response.blob();
        const disposition = response.headers.get("Content-Disposition") || "";
        const matched = disposition.match(/filename\*=utf-8''(.+)/i);
        const fileName = matched ? decodeURIComponent(matched[1]) : "班级免登录打卡链接快捷方式.zip";
        const blobUrl = URL.createObjectURL(blob);
        const anchor = document.createElement("a");
        anchor.href = blobUrl;
        anchor.download = fileName;
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        URL.revokeObjectURL(blobUrl);
        setStatus("linksStatus", `下载成功：${fileName}`, "success");
    }

    async function uploadStudentRoster() {
        const fileInput = document.getElementById("studentRosterFile");
        if (!fileInput.files.length) {
            setStatus("studentRosterStatus", "请先选择学生花名册 Excel 文件。", "error");
            return;
        }
        setStatus("studentRosterStatus", "正在导入学生花名册，请稍候...", null);
        setProgress("studentProgressBar", 0);
        try {
            const data = await uploadWithProgress("/api/admin/roster/students/upload", fileInput.files[0], "studentProgressBar");
            lastStudentImportResult = data;
            document.getElementById("exportStudentErrorsBtn").classList.toggle("hidden", !(data.errors && data.errors.length));
            setStatus("studentRosterStatus", buildImportStatus(data, "学生数据"), data.errorCount > 0 ? "error" : "success");
            await loadClassLinks();
        } catch (error) {
            setProgress("studentProgressBar", 0);
            setStatus("studentRosterStatus", error.message, "error");
        }
    }

    async function previewStudentRoster() {
        const fileInput = document.getElementById("studentRosterFile");
        if (!fileInput.files.length) {
            setStatus("studentRosterStatus", "请先选择学生花名册 Excel 文件。", "error");
            return;
        }
        setStatus("studentRosterStatus", "正在预校验学生花名册，请稍候...", null);
        setProgress("studentProgressBar", 0);
        try {
            const data = await uploadWithProgress("/api/admin/roster/students/preview", fileInput.files[0], "studentProgressBar");
            lastStudentImportResult = data;
            document.getElementById("exportStudentErrorsBtn").classList.toggle("hidden", !(data.errors && data.errors.length));
            setStatus("studentRosterStatus", buildImportStatus(data, "学生数据"), data.errorCount > 0 ? "error" : "success");
        } catch (error) {
            setProgress("studentProgressBar", 0);
            setStatus("studentRosterStatus", error.message, "error");
        }
    }

    async function uploadUserRoster() {
        const fileInput = document.getElementById("userRosterFile");
        if (!fileInput.files.length) {
            setStatus("userRosterStatus", "请先选择教职工账号 Excel 文件。", "error");
            return;
        }
        setStatus("userRosterStatus", "正在导入教职工账号，请稍候...", null);
        setProgress("userProgressBar", 0);
        try {
            const data = await uploadWithProgress("/api/admin/roster/users/upload", fileInput.files[0], "userProgressBar");
            lastUserImportResult = data;
            document.getElementById("exportUserErrorsBtn").classList.toggle("hidden", !(data.errors && data.errors.length));
            setStatus("userRosterStatus", buildImportStatus(data, "教职工账号"), data.errorCount > 0 ? "error" : "success");
            await loadClassLinks();
            await loadUserAccounts();
        } catch (error) {
            setProgress("userProgressBar", 0);
            setStatus("userRosterStatus", error.message, "error");
        }
    }

    async function previewUserRoster() {
        const fileInput = document.getElementById("userRosterFile");
        if (!fileInput.files.length) {
            setStatus("userRosterStatus", "请先选择教职工账号 Excel 文件。", "error");
            return;
        }
        setStatus("userRosterStatus", "正在预校验教职工账号，请稍候...", null);
        setProgress("userProgressBar", 0);
        try {
            const data = await uploadWithProgress("/api/admin/roster/users/preview", fileInput.files[0], "userProgressBar");
            lastUserImportResult = data;
            document.getElementById("exportUserErrorsBtn").classList.toggle("hidden", !(data.errors && data.errors.length));
            setStatus("userRosterStatus", buildImportStatus(data, "教职工账号"), data.errorCount > 0 ? "error" : "success");
        } catch (error) {
            setProgress("userProgressBar", 0);
            setStatus("userRosterStatus", error.message, "error");
        }
    }

    async function exportImportErrors(importType, errors) {
        const response = await api("/api/admin/roster/errors/export", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ importType, errors })
        });
        const blob = await response.blob();
        const disposition = response.headers.get("Content-Disposition") || "";
        const matched = disposition.match(/filename\*=utf-8''(.+)/i);
        const fileName = matched ? decodeURIComponent(matched[1]) : `${importType}导入错误清单.xlsx`;
        const blobUrl = URL.createObjectURL(blob);
        const anchor = document.createElement("a");
        anchor.href = blobUrl;
        anchor.download = fileName;
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        URL.revokeObjectURL(blobUrl);
    }

    async function saveSemesterDate() {
        const date = document.getElementById("semesterDate").value;
        if (!date) {
            setStatus("semesterStatus", "请选择开学日期。", "error");
            return;
        }
        setStatus("semesterStatus", "正在保存开学日期...", null);
        await api("/api/admin/semester-start", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ date })
        });
        await loadSemester();
        setStatus("semesterStatus", `保存成功：开学日期已更新为 ${date}`, "success");
    }

    async function saveDailyQuotes() {
        const morningText = document.getElementById("morningQuotesText").value;
        const eveningText = document.getElementById("eveningQuotesText").value;
        if (!morningText.trim() && !eveningText.trim()) {
            setStatus("dailyQuotesStatus", "早上金句和晚上金句至少填写一项。", "error");
            return;
        }
        const morningStartIndex = Number(document.getElementById("morningQuoteStartIndex").value || "1");
        const eveningStartIndex = Number(document.getElementById("eveningQuoteStartIndex").value || "1");
        setStatus("dailyQuotesStatus", "正在保存每日金句...", null);
        await api("/api/admin/daily-quotes", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                morningQuotesText: morningText,
                eveningQuotesText: eveningText,
                morningStartIndex,
                eveningStartIndex
            })
        });
        await loadDailyQuotes();
    }

    function getQuoteLines(period) {
        const element = document.getElementById(period === "morning" ? "morningQuotesText" : "eveningQuotesText");
        return ((element && element.value) || "")
            .split(/\r?\n/)
            .map(item => item.trim())
            .filter(Boolean);
    }

    function syncQuoteStartOptions(period, selectedValue) {
        const select = document.getElementById(period === "morning" ? "morningQuoteStartIndex" : "eveningQuoteStartIndex");
        if (!select) {
            return;
        }
        const quotes = getQuoteLines(period);
        const safeValue = Math.max(1, Math.min(Number(selectedValue || 1), Math.max(quotes.length, 1)));
        if (!quotes.length) {
            select.innerHTML = '<option value="1">今日从第 1 句开始显示</option>';
            select.value = "1";
            renderQuotePreview(period, "未设置", 1);
            return;
        }
        select.innerHTML = quotes.map((quote, index) => {
            const label = quote.length > 18 ? `${quote.slice(0, 18)}...` : quote;
            return `<option value="${index + 1}">今日从第 ${index + 1} 句开始显示：${label}</option>`;
        }).join("");
        select.value = String(safeValue);
    }

    function renderQuotePreview(period, todayQuote, todayIndex) {
        const preview = document.getElementById(period === "morning" ? "morningQuotePreview" : "eveningQuotePreview");
        if (!preview) {
            return;
        }
        preview.textContent = `${period === "morning" ? "今日早上显示预览" : "今日晚上显示预览"}：第 ${todayIndex || 1} 句：${todayQuote || "未设置"}`;
    }

    async function saveUserEdit() {
        const userId = Number(document.getElementById("editUserId").value);
        if (!userId) {
            setStatus("userEditStatus", "请先在账号列表中选择要编辑的账号。", "error");
            return;
        }
        const payload = {
            userId,
            realName: document.getElementById("editRealName").value,
            roleCode: document.getElementById("editRoleCode").value,
            gradeName: document.getElementById("editGradeName").value,
            classId: document.getElementById("editClassId").value ? Number(document.getElementById("editClassId").value) : null,
            enabled: Number(document.getElementById("editEnabled").value)
        };
        if (!payload.realName || !payload.realName.trim()) {
            setStatus("userEditStatus", "姓名不能为空。", "error");
            return;
        }
        if (payload.roleCode === "TEACHER" && !payload.classId) {
            setStatus("userEditStatus", "班主任账号必须绑定班级。", "error");
            return;
        }
        if (payload.roleCode === "GRADE_LEADER" && !payload.gradeName) {
            setStatus("userEditStatus", "级长账号必须绑定年级。", "error");
            return;
        }
        setStatus("userEditStatus", "正在保存账号信息...", null);
        await api("/api/admin/users/update", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });
        await Promise.all([loadUserAccounts(), loadClassLinks()]);
        setStatus("userEditStatus", "账号信息保存成功。", "success");
        window.setTimeout(() => {
            closeModal(userEditModal);
        }, 400);
    }

    async function saveCheckinDebug() {
        const enabled = document.getElementById("checkinDebugEnabled").value === "1";
        setStatus("checkinDebugStatus", "正在保存调试开关...", null);
        await api("/api/admin/checkin-debug", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ enabled })
        });
        await loadCheckinDebugSetting();
    }

    async function changeOwnPassword() {
        const oldPassword = document.getElementById("oldPassword").value;
        const newPassword = document.getElementById("newPassword").value;
        if (!oldPassword || !newPassword) {
            setStatus("passwordStatus", "请完整填写原密码和新密码。", "error");
            return;
        }
        setStatus("passwordStatus", "正在修改密码...", null);
        const data = await api("/api/admin/password/change", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ oldPassword, newPassword })
        });
        localStorage.setItem(storageKey, JSON.stringify(data));
        setStatus("passwordStatus", "密码修改成功，当前登录状态已刷新。", "success");
        auth.token = data.token;
        auth.mustChangePassword = data.mustChangePassword;
        document.getElementById("oldPassword").value = "";
        document.getElementById("newPassword").value = "";
        window.setTimeout(() => window.location.reload(), 500);
    }

    async function downloadTemplate(apiUrl, statusId, defaultName) {
        setStatus(statusId, "正在下载模板...", null);
        const response = await api(apiUrl);
        const blob = await response.blob();
        const disposition = response.headers.get("Content-Disposition") || "";
        const matched = disposition.match(/filename\*=utf-8''(.+)/i);
        const fileName = matched ? decodeURIComponent(matched[1]) : defaultName;
        const blobUrl = URL.createObjectURL(blob);
        const anchor = document.createElement("a");
        anchor.href = blobUrl;
        anchor.download = fileName;
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        URL.revokeObjectURL(blobUrl);
        setStatus(statusId, `模板下载成功：${fileName}`, "success");
    }

    function showError(error) {
        alert(error.message || "操作失败");
    }

    document.getElementById("refreshLeaderboardBtn").addEventListener("click", () => {
        loadLeaderboard().catch(showError);
    });
    if (isAdmin) {
        document.getElementById("leaderboardGradeFilter").addEventListener("change", () => {
            fillLeaderboardFilters();
            studentQualityPage = 1;
            studentQualityPage = 1;
            renderLeaderboardRows();
            renderStudentQualityRows();
            const selectedClassId = document.getElementById("leaderboardClassFilter").value;
            if (selectedClassId) {
                showClassLeaderboardDetail(Number(selectedClassId), getSelectedLeaderboardClassLabel()).catch(showError);
            } else {
                hideClassLeaderboardDetail();
            }
        });
        document.getElementById("leaderboardClassFilter").addEventListener("change", () => {
            renderLeaderboardRows();
            renderStudentQualityRows();
            const selectedClassId = document.getElementById("leaderboardClassFilter").value;
            if (selectedClassId) {
                showClassLeaderboardDetail(Number(selectedClassId), getSelectedLeaderboardClassLabel()).catch(showError);
            } else {
                hideClassLeaderboardDetail();
            }
        });
        document.getElementById("studentQualityPrevBtn").addEventListener("click", () => {
            if (studentQualityPage > 1) {
                studentQualityPage -= 1;
                renderStudentQualityRows();
            }
        });
        document.getElementById("studentQualityNextBtn").addEventListener("click", () => {
            studentQualityPage += 1;
            renderStudentQualityRows();
        });
        document.getElementById("openLeaderboardClassBtn").addEventListener("click", () => {
            const selectedClassId = document.getElementById("leaderboardClassFilter").value;
            if (!selectedClassId) {
                setStatus("exportStatus", "请先选择班级后再查看班级学生榜。", "error");
                return;
            }
            showClassLeaderboardDetail(Number(selectedClassId), getSelectedLeaderboardClassLabel()).catch(showError);
        });
    }
    document.getElementById("changePasswordBtn").addEventListener("click", () => {
        changeOwnPassword().catch((error) => {
            setStatus("passwordStatus", error.message, "error");
        });
    });
    document.getElementById("exportScope").addEventListener("change", updateExportFields);
    document.getElementById("exportBtn").addEventListener("click", () => {
        exportReport().catch(showError);
    });
    document.getElementById("loadCorrectionBtn").addEventListener("click", () => {
        loadCorrectionRows().catch(showError);
    });
    document.getElementById("correctionSlot").addEventListener("change", () => {
        syncCorrectionTimeDefault();
    });
    document.getElementById("closeCorrectionCheckinModalBtn").addEventListener("click", () => {
        closeModal(correctionCheckinModal);
    });
    document.querySelectorAll("[data-close-modal='correctionCheckinModal']").forEach((element) => {
        element.addEventListener("click", () => {
            closeModal(correctionCheckinModal);
        });
    });
    document.getElementById("confirmCorrectionCheckinBtn").addEventListener("click", () => {
        confirmCorrectionCheckin().catch((error) => {
            setStatus("correctionModalStatus", error.message, "error");
        });
    });

    if (isAdmin) {
        document.getElementById("downloadStudentTemplateBtn").addEventListener("click", () => {
            downloadTemplate("/api/admin/roster/students/template", "studentRosterStatus", "学生花名册导入模板.xlsx").catch((error) => {
                setStatus("studentRosterStatus", error.message, "error");
            });
        });
        document.getElementById("previewStudentRosterBtn").addEventListener("click", () => {
            previewStudentRoster().catch(showError);
        });
        document.getElementById("downloadUserTemplateBtn").addEventListener("click", () => {
            downloadTemplate("/api/admin/roster/users/template", "userRosterStatus", "教职工账号导入模板.xlsx").catch((error) => {
                setStatus("userRosterStatus", error.message, "error");
            });
        });
        document.getElementById("previewUserRosterBtn").addEventListener("click", () => {
            previewUserRoster().catch(showError);
        });
        document.getElementById("uploadStudentRosterBtn").addEventListener("click", () => {
            uploadStudentRoster().catch(showError);
        });
        document.getElementById("uploadUserRosterBtn").addEventListener("click", () => {
            uploadUserRoster().catch(showError);
        });
        document.getElementById("exportStudentErrorsBtn").addEventListener("click", () => {
            if (lastStudentImportResult && lastStudentImportResult.errors && lastStudentImportResult.errors.length) {
                exportImportErrors("学生数据", lastStudentImportResult.errors).catch((error) => {
                    setStatus("studentRosterStatus", error.message, "error");
                });
            }
        });
        document.getElementById("exportUserErrorsBtn").addEventListener("click", () => {
            if (lastUserImportResult && lastUserImportResult.errors && lastUserImportResult.errors.length) {
                exportImportErrors("教职工账号", lastUserImportResult.errors).catch((error) => {
                    setStatus("userRosterStatus", error.message, "error");
                });
            }
        });
        document.getElementById("saveSemesterBtn").addEventListener("click", () => {
            saveSemesterDate().catch(showError);
        });
        document.getElementById("saveCheckinDebugBtn").addEventListener("click", () => {
            saveCheckinDebug().catch((error) => {
                setStatus("checkinDebugStatus", error.message, "error");
            });
        });
        document.getElementById("saveEveningCheckinBtn").addEventListener("click", () => {
            saveEveningCheckinSettings().catch((error) => {
                setStatus("eveningCheckinStatus", error.message, "error");
            });
        });
        document.getElementById("closeUserEditModalBtn").addEventListener("click", () => {
            closeModal(userEditModal);
        });
        document.querySelectorAll("[data-close-modal='userEditModal']").forEach((element) => {
            element.addEventListener("click", () => {
                closeModal(userEditModal);
            });
        });
        document.getElementById("editRoleCode").addEventListener("change", () => {
            updateUserEditFieldsByRole();
        });
        document.getElementById("editGradeName").addEventListener("change", (event) => {
            fillEditClassOptions(event.target.value, "");
            updateUserEditFieldsByRole();
        });
        document.getElementById("saveUserEditBtn").addEventListener("click", () => {
            saveUserEdit().catch((error) => {
                setStatus("userEditStatus", error.message, "error");
            });
        });
        document.getElementById("saveDailyQuotesBtn").addEventListener("click", () => {
            saveDailyQuotes().catch((error) => {
                setStatus("dailyQuotesStatus", error.message, "error");
            });
        });
        document.getElementById("rankMode").addEventListener("change", () => {
            updateRankModeView();
        });
        document.getElementById("saveRankSettingBtn").addEventListener("click", () => {
            saveRankSettings().catch((error) => {
                setStatus("rankSettingStatus", error.message, "error");
            });
        });
        document.getElementById("morningQuotesText").addEventListener("input", () => {
            const currentValue = document.getElementById("morningQuoteStartIndex").value || "1";
            syncQuoteStartOptions("morning", currentValue);
            const quotes = getQuoteLines("morning");
            const selected = Number(document.getElementById("morningQuoteStartIndex").value || "1");
            renderQuotePreview("morning", quotes.length ? quotes[selected - 1] : "未设置", selected);
        });
        document.getElementById("eveningQuotesText").addEventListener("input", () => {
            const currentValue = document.getElementById("eveningQuoteStartIndex").value || "1";
            syncQuoteStartOptions("evening", currentValue);
            const quotes = getQuoteLines("evening");
            const selected = Number(document.getElementById("eveningQuoteStartIndex").value || "1");
            renderQuotePreview("evening", quotes.length ? quotes[selected - 1] : "未设置", selected);
        });
        document.getElementById("morningQuoteStartIndex").addEventListener("change", (event) => {
            const quotes = getQuoteLines("morning");
            const selected = Number(event.target.value || "1");
            renderQuotePreview("morning", quotes.length ? quotes[selected - 1] : "未设置", selected);
        });
        document.getElementById("eveningQuoteStartIndex").addEventListener("change", (event) => {
            const quotes = getQuoteLines("evening");
            const selected = Number(event.target.value || "1");
            renderQuotePreview("evening", quotes.length ? quotes[selected - 1] : "未设置", selected);
        });
        document.getElementById("exportClassLinksBtn").addEventListener("click", () => {
            exportClassLinks().catch((error) => {
                setStatus("linksStatus", error.message, "error");
            });
        });
        document.getElementById("downloadClassLinkZipBtn").addEventListener("click", () => {
            downloadClassLinkZip().catch((error) => {
                setStatus("linksStatus", error.message, "error");
            });
        });
        document.getElementById("loadStudentOrderBtn").addEventListener("click", () => {
            loadStudentOrderBindings().catch(showError);
        });
        document.getElementById("studentOrderClassId").addEventListener("change", () => {
            loadStudentOrderBindings().catch(showError);
        });
    } else {
        ["downloadStudentTemplateBtn", "uploadStudentRosterBtn", "downloadUserTemplateBtn", "uploadUserRosterBtn"].forEach((id) => {
            const element = document.getElementById(id);
            if (element) {
                element.classList.add("hidden");
            }
        });
    }

    updateExportFields();
    syncCorrectionTimeDefault();
    if (isAdmin) {
        fillEditGradeOptions("");
        fillEditClassOptions("", "");
        updateUserEditFieldsByRole();
    }
    if (auth.mustChangePassword) {
        navLinks.forEach((button) => {
            if (button.dataset.section !== "password") {
                button.disabled = true;
                button.style.opacity = "0.5";
                button.style.cursor = "not-allowed";
            }
        });
        activateSection("password");
        setStatus("passwordStatus", "首次登录后必须先修改密码。", "error");
    } else {
        (async () => {
            await Promise.all([
                loadSemester(),
                loadOverviewStats(),
                isAdmin ? loadCheckinDebugSetting() : Promise.resolve(),
                isAdmin ? loadDailyQuotes() : Promise.resolve(),
                isAdmin ? loadRankSettings() : Promise.resolve(),
                loadLeaderboard(),
                isAdmin ? loadUserAccounts() : Promise.resolve(),
                loadCorrectionClasses()
            ]);
            if (isAdmin) {
                await loadAccessBaseUrl();
                await loadClassLinks();
                await loadEveningCheckinSettings();
            }
        })().catch((error) => {
            if ((error.message || "").includes("未登录") || (error.message || "").includes("失效")) {
                localStorage.removeItem(storageKey);
                location.replace("/admin/login.html");
                return;
            }
            showError(error);
        });
    }
})();





