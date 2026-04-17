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
    const canvas = document.getElementById('boxplotChart');
    const ctx = canvas.getContext('2d');

    // Уничтожаем старый график, если есть
    if (charts.boxplot) {
        charts.boxplot.destroy();
        charts.boxplot = null;
    }

    if (!boxPlotData || Object.keys(boxPlotData).length === 0) {
        console.warn('No boxplot data');
        return;
    }

    const branches = Object.keys(boxPlotData);
    const values = [];

    // Собираем все значения для определения диапазона Y
    for (const branch of branches) {
        const data = boxPlotData[branch];
        values.push(data.min, data.max, data.q1, data.q3, data.median);
        if (data.outliers) values.push(...data.outliers);
    }

    const minY = Math.min(...values) - 5;
    const maxY = Math.max(...values) + 5;

    // Очищаем canvas
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    // Настройки отрисовки
    const width = canvas.width;
    const height = canvas.height;
    const leftMargin = 80;
    const rightMargin = 40;
    const topMargin = 40;
    const bottomMargin = 60;
    const chartWidth = width - leftMargin - rightMargin;
    const chartHeight = height - topMargin - bottomMargin;

    // Функции для преобразования координат
    const getX = (index) => {
        const step = chartWidth / (branches.length - 1 || 1);
        return leftMargin + index * step;
    };

    const getY = (value) => {
        return topMargin + chartHeight - ((value - minY) / (maxY - minY)) * chartHeight;
    };

    // Рисуем оси
    ctx.save();
    ctx.strokeStyle = '#ccc';
    ctx.fillStyle = '#333';
    ctx.font = '12px Arial';

    // Ось Y
    ctx.beginPath();
    ctx.moveTo(leftMargin, topMargin);
    ctx.lineTo(leftMargin, topMargin + chartHeight);
    ctx.lineTo(width - rightMargin, topMargin + chartHeight);
    ctx.stroke();

    // Подписи оси Y
    const ySteps = 5;
    for (let i = 0; i <= ySteps; i++) {
        const value = minY + (maxY - minY) * (i / ySteps);
        const y = getY(value);
        ctx.fillText(value.toFixed(1), leftMargin - 35, y + 4);
        ctx.beginPath();
        ctx.moveTo(leftMargin - 5, y);
        ctx.lineTo(leftMargin, y);
        ctx.stroke();
    }

    // Подписи оси X
    branches.forEach((branch, idx) => {
        const x = getX(idx);
        ctx.fillText(branch, x - 20, topMargin + chartHeight + 20);
    });

    // Рисуем box plot для каждого филиала
    const boxWidth = 40;

    branches.forEach((branch, idx) => {
        const data = boxPlotData[branch];
        const x = getX(idx);
        const boxLeft = x - boxWidth / 2;
        const boxTop = getY(data.q3);
        const boxBottom = getY(data.q1);
        const medianY = getY(data.median);
        const minYpos = getY(data.min);
        const maxYpos = getY(data.max);

        // Ящик (Q1 - Q3)
        ctx.fillStyle = 'rgba(54, 162, 235, 0.5)';
        ctx.fillRect(boxLeft, boxTop, boxWidth, boxBottom - boxTop);
        ctx.strokeStyle = '#333';
        ctx.lineWidth = 1;
        ctx.strokeRect(boxLeft, boxTop, boxWidth, boxBottom - boxTop);

        // Медиана
        ctx.beginPath();
        ctx.moveTo(boxLeft, medianY);
        ctx.lineTo(boxLeft + boxWidth, medianY);
        ctx.lineWidth = 2;
        ctx.stroke();

        // Усы
        ctx.beginPath();
        ctx.moveTo(x, minYpos);
        ctx.lineTo(x, maxYpos);
        ctx.lineWidth = 1;
        ctx.stroke();

        // Горизонтальные линии на усах
        ctx.beginPath();
        ctx.moveTo(x - 10, minYpos);
        ctx.lineTo(x + 10, minYpos);
        ctx.moveTo(x - 10, maxYpos);
        ctx.lineTo(x + 10, maxYpos);
        ctx.stroke();

        // Выбросы (если есть)
        if (data.outliers && data.outliers.length > 0) {
            ctx.fillStyle = 'red';
            data.outliers.forEach(outlier => {
                const outlierY = getY(outlier);
                ctx.beginPath();
                ctx.arc(x, outlierY, 4, 0, 2 * Math.PI);
                ctx.fill();
            });
        }
    });

    // Заголовок
    ctx.fillStyle = '#333';
    ctx.font = 'bold 14px Arial';
    ctx.fillText('Box plot (распределение показателя по филиалам)', width / 2 - 150, 25);

    ctx.restore();
}

function drawBarChart(means, confidenceIntervals) {
    const canvas = document.getElementById('barChart');
    if (!canvas) return;

    const ctx = canvas.getContext('2d');

    if (!means || Object.keys(means).length === 0) return;

    const branches = Object.keys(means);

// ✅ Учитываем доверительные интервалы
    const allUpper = branches.map(b => means[b] + (confidenceIntervals?.[b] || 0));
    const allLower = branches.map(b => means[b] - (confidenceIntervals?.[b] || 0));

    const maxVal = Math.max(...allUpper) + 5;
    const minVal = Math.min(...allLower) - 5;

    ctx.clearRect(0, 0, canvas.width, canvas.height);

    const width = canvas.width;
    const height = canvas.height;
    const left = 80, right = 40, top = 40, bottom = 60;
    const chartW = width - left - right;
    const chartH = height - top - bottom;

    const getY = (v) => top + chartH - ((v - minVal) / (maxVal - minVal)) * chartH;

// Оси
    ctx.beginPath();
    ctx.moveTo(left, top);
    ctx.lineTo(left, top + chartH);
    ctx.lineTo(width - right, top + chartH);
    ctx.stroke();

// Подписи Y
    for (let i = 0; i <= 5; i++) {
        const val = minVal + (maxVal - minVal) * i / 5;
        const y = getY(val);
        ctx.fillText(val.toFixed(1), left - 40, y + 4);

        ctx.beginPath();
        ctx.moveTo(left - 5, y);
        ctx.lineTo(left, y);
        ctx.stroke();
    }

    const barWidth = Math.min(60, chartW / branches.length * 0.6);
    const spacing = branches.length > 1
        ? (chartW - barWidth * branches.length) / (branches.length - 1)
        : 0;

    branches.forEach((branch, idx) => {
        const mean = means[branch];
        const ci = confidenceIntervals?.[branch] || 0;

        const x = left + idx * (barWidth + spacing);

        const barTop = getY(mean);
        const barBottom = getY(minVal); // ✅ фикс вместо getY(0)

        // Столбик
        ctx.fillStyle = 'rgba(52, 152, 219, 0.7)';
        ctx.fillRect(x, barTop, barWidth, barBottom - barTop);
        ctx.strokeRect(x, barTop, barWidth, barBottom - barTop);

        // CI
        const upperY = getY(mean + ci);
        const lowerY = getY(mean - ci);

        ctx.strokeStyle = '#e74c3c';
        ctx.lineWidth = 2;

        ctx.beginPath();
        ctx.moveTo(x + barWidth / 2, upperY);
        ctx.lineTo(x + barWidth / 2, lowerY);
        ctx.stroke();

        ctx.beginPath();
        ctx.moveTo(x + barWidth / 2 - 6, upperY);
        ctx.lineTo(x + barWidth / 2 + 6, upperY);
        ctx.moveTo(x + barWidth / 2 - 6, lowerY);
        ctx.lineTo(x + barWidth / 2 + 6, lowerY);
        ctx.stroke();

        // Подписи
        ctx.fillStyle = '#333';
        ctx.font = '12px Arial';
        ctx.fillText(branch, x + barWidth / 2 - 20, top + chartH + 20);

        ctx.font = 'bold 12px Arial';
        ctx.fillText(mean.toFixed(1), x + barWidth / 2 - 12, barTop - 8);
    });

// Заголовок
    ctx.font = 'bold 14px Arial';
    ctx.fillStyle = '#333';
    ctx.fillText(
        'Средние значения с доверительными интервалами (95%)',
        width / 2 - 180,
        25
    );

}

function drawDotplot(allValues) {
    const ctx = document.getElementById('dotplotChart').getContext('2d');
    if (charts.dotplot) charts.dotplot.destroy();

    if (!allValues) return;

    const branches = Object.keys(allValues);

    const datasets = branches.map((branch, idx) => ({
        label: branch,
        data: allValues[branch].map(val => ({
            x: branch, // ✅ теперь категория, а не число
            y: val
        })),
        backgroundColor: `hsl(${idx * 360 / branches.length}, 70%, 50%)`,
        pointRadius: 5
    }));

    charts.dotplot = new Chart(ctx, {
        type: 'scatter',
        data: { datasets },
        options: {
            responsive: true,
            scales: {
                x: {
                    type: 'category', // ✅ ключевой фикс
                    labels: branches,
                    title: { display: true, text: 'Филиалы' }
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