// API endpoints
const API_BASE = '/api/v1/analyzer';

let currentFile = null;
let currentMetricType = null;
let currentMetricName = null;
let analysisResult = null;
let charts = {};

// Метрики по типам компаний
const metrics = {
    MARKETING: [
        "Объём продаж на сотрудника (руб./мес.)",
        "Выполнение плана продаж (%)",
        "Выручка на один час работы сотрудника (руб.)"
    ],
    PRODUCTION: [
        "Количество обработанных заказов на сотрудника",
        "Количество произведённых единиц продукции за смену",
        "Процент брака на сотрудника (%)"
    ],
    TECHNICAL_SUPPORT: [
        "Количество закрытых обращений/тикетов на сотрудника",
        "Среднее время ответа клиенту (сек)",
        "Оценка качества от клиентов (1-5)"
    ]
};

// Инициализация
document.addEventListener('DOMContentLoaded', () => {
    initMetricsRadios();

    document.getElementById('companyType').addEventListener('change', () => initMetricsRadios());
    document.getElementById('importBtn').addEventListener('click', () => document.getElementById('fileInput').click());
    document.getElementById('fileInput').addEventListener('change', handleFileImport);
    document.getElementById('analyzeBtn').addEventListener('click', runAnalysis);
    document.getElementById('exportBtn').addEventListener('click', exportToPDF);
});

function initMetricsRadios() {
    const companyType = document.getElementById('companyType').value;
    const metricsList = metrics[companyType];
    const container = document.getElementById('metricsRadios');

    container.innerHTML = '';
    metricsList.forEach((metric, index) => {
        const label = document.createElement('label');
        const radio = document.createElement('input');
        radio.type = 'radio';
        radio.name = 'metric';
        radio.value = metric;
        radio.checked = index === 0;
        radio.addEventListener('change', () => {
            if (radio.checked) currentMetricName = metric;
        });
        label.appendChild(radio);
        label.appendChild(document.createTextNode(metric));
        container.appendChild(label);
    });

    currentMetricName = metricsList[0];
    currentMetricType = companyType;
}

function handleFileImport(event) {
    const file = event.target.files[0];
    if (!file) return;

    currentFile = file;
    document.getElementById('fileName').textContent = file.name;
    document.getElementById('analyzeBtn').disabled = false;

    // Показываем превью файла
    showFilePreview(file);
}

async function showFilePreview(file) {
    const previewDiv = document.getElementById('dataPreview');
    const tableDiv = document.getElementById('previewTable');

    // Читаем первые строки для превью
    const extension = file.name.split('.').pop().toLowerCase();

    if (extension === 'csv') {
        const text = await file.text();
        const lines = text.trim().split('\n');
        const headers = lines[0].split(',').map(h => h.trim());
        const rows = lines.slice(1, 11).map(line => line.split(',').map(cell => cell.trim()));

        displayPreviewTable(headers, rows);
    } else if (extension === 'xlsx') {
        const data = await file.arrayBuffer();
        const workbook = XLSX.read(data, { type: 'array' });
        const sheet = workbook.Sheets[workbook.SheetNames[0]];
        const jsonData = XLSX.utils.sheet_to_json(sheet, { header: 1 });

        const headers = jsonData[0].map(h => String(h).trim());
        const rows = jsonData.slice(1, 11).map(row => row.map(cell => String(cell).trim()));

        displayPreviewTable(headers, rows);
    }

    previewDiv.style.display = 'block';
}

function displayPreviewTable(headers, rows) {
    let html = '<table><thead><tr>';
    headers.forEach(h => { html += `<th>${h}</th>`; });
    html += '</tr></thead><tbody>';

    rows.forEach(row => {
        html += '<tr>';
        row.forEach(cell => { html += `<td>${cell}</td>`; });
        html += '</tr>';
    });
    html += '</tbody></table>';

    document.getElementById('previewTable').innerHTML = html;
}

async function runAnalysis() {
    if (!currentFile) {
        alert('Пожалуйста, выберите файл для анализа');
        return;
    }

    const analyzeBtn = document.getElementById('analyzeBtn');
    analyzeBtn.disabled = true;
    analyzeBtn.textContent = 'Анализ...';

    const formData = new FormData();
    formData.append('file', currentFile);
    formData.append('metricType', currentMetricType);

    try {
        const response = await fetch(`${API_BASE}/analyze`, {
            method: 'POST',
            body: formData
        });

        const result = await response.json();

        if (result.data) {
            analysisResult = result.data;
            displayResults(analysisResult);
            document.getElementById('results').style.display = 'block';
            document.getElementById('exportBtn').style.display = 'block';
        } else {
            alert('Ошибка при анализе: ' + (result.message || 'Неизвестная ошибка'));
        }
    } catch (error) {
        console.error('Error:', error);
        alert('Ошибка при выполнении анализа');
    } finally {
        analyzeBtn.disabled = false;
        analyzeBtn.textContent = '🔍 Провести анализ';
    }

    try {
        const response = await fetch(`${API_BASE}/analyze`, {
            method: 'POST',
            body: formData
        });

        const result = await response.json();

        // 👇 ПОДРОБНАЯ ОТЛАДКА
        console.log('=== ПОЛНЫЙ ОТВЕТ ОТ СЕРВЕРА ===');
        console.log(JSON.stringify(result, null, 2));
        console.log('=== anovaTable ===');
        console.log(result.data?.anovaTable);
        console.log('Все ключи в anovaTable:', Object.keys(result.data?.anovaTable || {}));

        if (result.data) {
            analysisResult = result.data;
            displayResults(analysisResult);
            // ...
        }
    } catch (error) {
        console.error('Error:', error);
        alert('Ошибка при выполнении анализа');
    }
}

function displayResults(result) {
    // 1. Исходные данные
    displayRawData(result.rawData);

    // 2. Статистика по филиалам
    displayBranchStatistics(result.branchStatistics);

    // 3. Тест гомогенности
    displayHomogeneityTest(result.homogeneityTest);

    // 4. ANOVA
    displayANOVA(result.anovaTable);

    // 5. Post-hoc тесты
    displayPostHoc(result.postHocTests);

    // 6. Ранжирование
    displayRanking(result.ranking);

    // 7. Графики
    drawBoxplot(result.chartsData?.boxPlotData);
    drawBarChart(result.chartsData?.means, result.chartsData?.confidenceIntervals);
    drawDotplot(result.chartsData?.allValues);

}

function displayRawData(rawData) {
    if (!rawData || rawData.length === 0) return;

    let html = '<table><thead><tr><th>Сотрудник</th><th>Филиал</th><th>Показатель</th></tr></thead><tbody>';

    rawData.forEach(row => {
        html += `<tr><td>${row.employee || ''}</td><td>${row.branch || ''}</td><td>${row.value || ''}</td></tr>`;
    });
    html += '</tbody></table>';

    document.getElementById('rawDataTable').innerHTML = html;
}

function displayBranchStatistics(stats) {
    if (!stats) return;

    let html = '<table><thead><tr><th>Филиал</th><th>Кол-во</th><th>Среднее</th><th>Дисперсия</th><th>StdDev</th><th>Min/Max</th></tr></thead><tbody>';

    for (const [branch, stat] of Object.entries(stats)) {
        html += `<tr>
            <td>${branch}</td>
            <td>${stat.count}</td>
            <td>${stat.mean.toFixed(2)}</td>
            <td>${stat.variance.toFixed(2)}</td>
            <td>${stat.stdDev.toFixed(2)}</td>
            <td>${stat.min.toFixed(1)}/${stat.max.toFixed(1)}</td>
        </tr>`;
    }
    html += '</tbody></table>';

    document.getElementById('branchStatisticsTable').innerHTML = html;
}

function displayHomogeneityTest(test) {
    if (!test) {
        document.getElementById('leveneTable').innerHTML = '<p>Данные теста гомогенности отсутствуют</p>';
        return;
    }

    const statistic = test.statistic !== undefined ? test.statistic.toFixed(4) : 'N/A';
    let pvalueDisplay = 'N/A';
    if (test.pvalue !== undefined && test.pvalue !== null) {
        const pVal = parseFloat(test.pvalue);
        if (!isNaN(pVal)) {
            if (pVal < 0.001) {
                pvalueDisplay = '< 0.001';
            } else {
                pvalueDisplay = pVal.toFixed(6);
            }
        }
    }
    const interpretation = test.homogeneous !== undefined
        ? (test.homogeneous ? '✅ Дисперсии гомогенны' : '⚠️ Дисперсии НЕ гомогенны')
        : 'Нет данных';

    const html = `<table><thead><tr><th>Статистика</th><th>p-value</th><th>Интерпретация</th> </thead>
                  <tbody><tr>
                    <td>${statistic}</td>
                    <td>${pvalueDisplay}</td>
                    <td>${interpretation}</td>
                </tr></tbody></table>`;

    document.getElementById('leveneTable').innerHTML = html;
}

function displayANOVA(anova) {
    if (!anova) {
        document.getElementById('anovaTable').innerHTML = '<p>Данные ANOVA отсутствуют</p>';
        return;
    }

    let pvalueDisplay = 'N/A';
    if (anova.pvalue !== undefined && anova.pvalue !== null) {
        const pVal = Number(anova.pvalue);
        if (!isNaN(pVal)) {
            if (pVal < 0.001) {
                pvalueDisplay = '< 0.001';
            } else if (pVal < 0.01) {
                pvalueDisplay = pVal.toExponential(4);
            } else {
                pvalueDisplay = pVal.toFixed(6);
            }
        }
    } else if (anova.p !== undefined && anova.p !== null) {
        const pVal = Number(anova.p);
        if (!isNaN(pVal)) {
            if (pVal < 0.001) {
                pvalueDisplay = '< 0.001';
            } else if (pVal < 0.01) {
                pvalueDisplay = pVal.toExponential(4);
            } else {
                pvalueDisplay = pVal.toFixed(6);
            }
        }
    }

    // Добавим отладку в консоль
    console.log('ANOVA pvalue raw:', anova.pvalue);
    console.log('ANOVA pvalue display:', pvalueDisplay);

    const html = `<table><thead><th>Источник</th><th>SS</th><th>df</th><th>MS</th><th>F</th><th>p-value</th> </thead>
                  <tbody><tr>
                    <td>${anova.source || 'Между группами'}</td>
                    <td>${anova.ss !== undefined ? anova.ss.toFixed(2) : 'N/A'}</td>
                    <td>${anova.df !== undefined ? anova.df : 'N/A'}</td>
                    <td>${anova.ms !== undefined ? anova.ms.toFixed(2) : 'N/A'}</td>
                    <td>${anova.f !== undefined ? anova.f.toFixed(4) : 'N/A'}</td>
                    <td>${pvalueDisplay}</td>
                </tr></tbody></table>`;

    document.getElementById('anovaTable').innerHTML = html;
}

function displayPostHoc(postHoc) {
    if (!postHoc || postHoc.length === 0) {
        document.getElementById('posthocTable').innerHTML = '<p>Post-hoc тесты не проводились</p>';
        return;
    }

    let html = `<table><thead><tr><th>Пары филиалов</th><th>Разница средних</th><th>SE</th><th>p-value</th><th>НЗР</th><th>Значимо</th> </thead><tbody>`;

    postHoc.forEach(item => {
        let pvalueDisplay = 'N/A';
        if (item.pvalue !== undefined && item.pvalue !== null) {
            const pVal = parseFloat(item.pvalue);
            if (!isNaN(pVal)) {
                if (pVal < 0.001) {
                    pvalueDisplay = '< 0.001';
                } else {
                    pvalueDisplay = pVal.toFixed(6);
                }
            }
        }
        html += `<tr>
            <td>${item.pair || 'N/A'}</td>
            <td>${item.meanDiff !== undefined ? item.meanDiff.toFixed(2) : 'N/A'}</td>
            <td>${item.se !== undefined ? item.se.toFixed(4) : 'N/A'}</td>
            <td>${pvalueDisplay}</td>
            <td>${item.hsd !== undefined ? item.hsd.toFixed(2) : 'N/A'}</td>
            <td>${item.significant ? '✅ Да' : '❌ Нет'}</td>
        </tr>`;
    });
    html += '</tbody></table>';

    document.getElementById('posthocTable').innerHTML = html;
}
function displayRanking(ranking) {
    if (!ranking || ranking.length === 0) {
        document.getElementById('rankingTable').innerHTML = '<p>Ранжирование не выполнено</p>';
        return;
    }

    let html = '<table class="results-table"><thead><tr>';
    html += '<th>Ранг</th><th>Филиал</th><th>Среднее значение</th>';
    html += '</tr></thead><tbody>';

    ranking.forEach((item, idx) => {
        html += '<tr>';
        html += `<td>${idx + 1}</td>`;
        html += `<td>${item.branch || 'N/A'}</td>`;
        html += `<td>${item.mean !== undefined ? item.mean.toFixed(2) : 'N/A'}</td>`;
        html += '</tr>';
    });
    html += '</tbody></table>';

    document.getElementById('rankingTable').innerHTML = html;
}

function drawBoxplot(boxPlotData) {
    const ctx = document.getElementById('boxplotChart').getContext('2d');
    if (charts.boxplot) charts.boxplot.destroy();

    if (!boxPlotData) {
        console.warn('No boxplot data');
        return;
    }

    const branches = Object.keys(boxPlotData);

    // Создаем данные для bar chart с ошибками
    const means = [];
    const errors = [];

    branches.forEach(branch => {
        const data = boxPlotData[branch];
        // Используем медиану как среднее для отображения
        means.push(data.median);
        errors.push((data.q3 - data.q1) / 2); // полуразмах между квартилями
    });

    charts.boxplot = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: branches,
            datasets: [{
                label: 'Медиана (с квартильным размахом)',
                data: means,
                backgroundColor: 'rgba(54, 162, 235, 0.5)',
                borderColor: 'rgba(54, 162, 235, 1)',
                borderWidth: 1
            }]
        },
        options: {
            responsive: true,
            plugins: {
                tooltip: {
                    callbacks: {
                        label: (context) => {
                            const idx = context.dataIndex;
                            const data = boxPlotData[branches[idx]];
                            return [
                                `Медиана: ${data.median.toFixed(2)}`,
                                `Q1: ${data.q1.toFixed(2)}`,
                                `Q3: ${data.q3.toFixed(2)}`,
                                `Min: ${data.min.toFixed(2)}`,
                                `Max: ${data.max.toFixed(2)}`
                            ];
                        }
                    }
                }
            },
            scales: {
                y: {
                    title: { display: true, text: 'Значение показателя' }
                }
            }
        }
    });
}

function drawBarChart(means, confidenceIntervals) {
    const ctx = document.getElementById('barChart').getContext('2d');
    if (charts.bar) charts.bar.destroy();

    if (!means) return;

    const branches = Object.keys(means);
    const meansArray = branches.map(b => means[b]);
    const errors = branches.map(b => confidenceIntervals ? confidenceIntervals[b] : 0);

    charts.bar = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: branches,
            datasets: [{
                label: 'Среднее значение',
                data: meansArray,
                backgroundColor: 'rgba(52, 152, 219, 0.6)'
            }]
        },
        options: {
            responsive: true,
            plugins: {
                tooltip: {
                    callbacks: {
                        label: (ctx) => `${ctx.raw.toFixed(2)} ± ${errors[ctx.dataIndex]?.toFixed(2) || '0'}`
                    }
                }
            }
        }
    });
}

function drawDotplot(allValues) {
    const ctx = document.getElementById('dotplotChart').getContext('2d');
    if (charts.dotplot) charts.dotplot.destroy();

    if (!allValues) return;

    const branches = Object.keys(allValues);
    const datasets = branches.map((branch, idx) => ({
        label: branch,
        data: allValues[branch].map((val, i) => ({ x: idx + 1 + (Math.random() - 0.5) * 0.3, y: val })),
        backgroundColor: `hsl(${idx * 360 / branches.length}, 70%, 50%)`,
        pointRadius: 5,
        type: 'scatter'
    }));

    charts.dotplot = new Chart(ctx, {
        data: { datasets },
        options: {
            responsive: true,
            scales: {
                x: {
                    title: { display: true, text: 'Филиалы' },
                    ticks: { callback: (val) => branches[Math.round(val) - 1] }
                }
            },
            plugins: {
                tooltip: {
                    callbacks: {
                        label: (ctx) => `${ctx.dataset.label}: ${ctx.raw.y}`
                    }
                }
            }
        }
    });
}

async function exportToPDF() {
    if (!analysisResult) {
        alert('Нет данных для экспорта. Сначала выполните анализ.');
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/export`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(analysisResult)
        });

        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `анализ_${new Date().toLocaleDateString('ru-RU')}.pdf`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);
    } catch (error) {
        console.error('Export error:', error);
        alert('Ошибка при экспорте PDF');
    }
}