# CONTINUIDAD DEL PROYECTO VORKAN CLIMB 2.0
Actualizado: 2026-11-06

## Para el siguiente chat
Usuario escribe: **CONTINUAR VORKAN 2.0**.
Leer este documento y comprobar el código y el último GitHub Actions antes de cambiar nada. Responder en español, con resultados concretos, sin hacer repetir el contexto. No afirmar que una APK está disponible sin verificar el archivo real. **No tocar VORKAN 1.0**.

## Repositorio y compilación
- Repo: https://github.com/davidivannun/vorkan-climb-2 , rama main.
- Código Android Kotlin: app/src/main/java/com/vorkan/climb2/MainActivity.kt
- Dependencias: app/build.gradle.kts; minSdk 26, targetSdk 35, Firebase Auth/Firestore, MediaPipe, ONNX, pdfbox-android.
- Cada commit a main activa GitHub Actions; APK firmada como artifact VORKAN-CLIMB-2-APK-firmada.
- Último run confirmado verde: https://github.com/davidivannun/vorkan-climb-2/actions/runs/42287923629 ; commit 96ed430d2676e3c9365e8288ec8e9b0a724e8bb6 (corrección saltos de línea vista PDF). Artifact 13537534496.
- Para dar APK directa, descargar artifact ZIP, extraer APK y **comprobar existencia real en /mnt/data** antes de enlace sandbox. Evitar mandar al usuario a GitHub si puede descargarse directamente.

## Decisiones del producto (MUY IMPORTANTES)
- La prioridad actual es **IMPORTAR PDF -> analizar -> mostrar/revisar -> guardar -> entrenar**, minimizando interacción del usuario.
- NO MODIFICAR el editor ni el flujo de creación de entrenamientos manuales: son sesiones de UN DÍA, funcionan y el usuario pide expresamente conservarlos. Se ampliará la biblioteca de ejercicios más adelante.
- El constructor manual de planes/semanas es secundario. Evitar configuraciones extensas, formularios y calendarios obligatorios.
- Para mesociclos: conservar semanas y sesiones según documento; siguiente sesión pendiente sin calendario obligatorio; calendario opcional futuro. No inventar número de semanas ni duplicar automáticamente cargas. Distinguir programado de realizado y conservar historial.
- PDF de diferentes entrenadores y formatos: reconocimiento robusto; datos ambiguos se presentan para corregir, jamás inventar. OCR de escaneados queda para después.
- Usuario prefiere avances reales en código, APK firmada descargable directamente por chat, explicaciones breves, sin promesas falsas.

## Estado del entrenamiento manual (NO TOCAR)
- Pantalla ENTRENAMIENTOS: importar PDF, PDF importados, Mis planes y semanas, Crear entrenamiento manual, Mis entrenamientos.
- Planes manuales en archivo privado vorkan-training-plans.json, JSONArray de {id,name,exercises}.
- Ejercicios: name, mode (Tiempo/Repeticiones), sets, amount, rest, between, weight, edge.
- showManualTrainingBuilder(), editTrainingExercise(), showSavedTrainingPlans(), showGuidedTraining().
- Runner guiado probado por usuario: suspensiones, dominadas, beeps, fases verdes/rojas, barra progreso, pantalla encendida, descansos entre ejercicios, cuenta atrás. Ligera desincronización sonido/reloj conocida, no prioritario.
- Conserva funcionalidades de rocódromos, Firebase, editor/detección de presas.

## Estado del importador PDF (implementado, limitado)
- ActivityResultContracts.OpenDocument() permite seleccionar application/pdf.
- PDFBox Android extrae texto local (PDFBoxResourceLoader.init; PDDocument.load; PDFTextStripper).
- Se identifican encabezados de día A/B/C, día opcional, semana; se extraen entradas con viñeta; vista previa por sección con AlertDialog.
- Datos se guardan localmente en vorkan-imported-pdfs.json como {id,name,rawText,sections,status:"pendiente_de_revision"}.
- La vista previa NO convierte aún ejercicios a parámetros ni alimenta runner. No afirmar que lo hace.
- Corrección reciente: joinToString con saltos de línea reales (antes se mostraban literalmente \\n\\n).
- Limitaciones: parseo heurístico y frágil para PDFs de entrenadores distintos; cabeceras y saltos de línea de PDF; aún no hay edición individual ni OCR; no hay gestión real de semanas/mesociclos importados.
- Posible defecto: al abrir un PDF ya guardado, showPdfTrainingPreview vuelve a mostrar GUARDAR y puede duplicarlo. Corregir en desarrollo posterior.
- PDF prueba subido por usuario: Plan_Entrenamiento_Escalada_ABC.pdf, 1 página. El usuario probó importación en teléfono y confirmó captura de 4 secciones detectadas, Día A muestra 4 ejercicios; único fallo visible fueron los caracteres \\n\\n, corregidos en commit 96ed430.
- Estructura PDF:
  * Día A Fuerza + Decisión: calentamiento 15min descanso 3min; bloques duros 45–60min, 4–6 bloques, máx 4 intentos, descanso 3–5min; suspensiones regleta 14mm, 3x7s +18–20kg descanso 3min; tracción explosiva 4rep x3series descanso 2:30.
  * Día B Continuidad: calentamiento 10–12min descanso 2min; EMOM bloques alternos 12–15min 6a sólido; continuidad larga 2 series x30–45 movimientos 6b/6b+ descanso 4min; repeaters regleta 14mm 2 series de 7s ON/7s OFF x6 rep descanso 3min; core 10min.
  * Día C ARC + Volumen: ARC técnico 30min continuo microdescansos <=3s; escalada fluida 20min 5c–6a; tracción ligera opcional EMOM 3 dominadas x8min.
  * Día cuerda opcional: 2 vías fáciles; 3 vías máximo encadenable descanso 10–15min; 1 vía fácil técnica; 1 intento proyecto si sobra energía.
  * Progresión condicional: Día A +1 bloque; Día B +3min EMOM; Día C +5min escalada fluida.
- Este PDF NO especifica cuántas semanas ni fechas. No inventarlas.

## Próximos pasos prioritarios
1. Validar en móvil corrección de vista previa del PDF.
2. Mejorar análisis: reconocer por ejercicio series, repeticiones, tiempo, carga, descansos, regleta, intervalos, notas, y tipos EMOM/repeaters; conservar texto fuente y marcar dudas.
3. Vista previa por sesión con ejercicios legibles y posibilidad de corregir solo lo necesario. No sobrecargar UI.
4. Convertir sesiones confirmadas a formato de entrenamiento guiado sin perder semántica de EMOM/repeaters. NO modificar editor manual salvo solicitud expresa; ampliar motor con compatibilidad.
5. Guardar mesociclo/semanas importadas con historial, siguiente sesión pendiente y sin calendario obligatorio.
6. Verificar cada commit compilando; si falla, leer logs y corregir. APK firmada directa en chat si posible.

## Código añadido recientemente
- adee0da: Mis planes y semanas (manual, secundario). Implementación inicial con avance de semana que reinicia estados; NO es historial robusto. No expandir antes de importador.
- 0e660f5: importador PDF y preview.
- e86b8f0: pdfbox-android dependencia.
- 96ed430: saltos de línea corregidos.
- Última captura del usuario: pantalla REVISAR PDF IMPORTADO, 4 secciones, Día A visible, separadores literales \\n\\n en diálogo; luego corrección y run verde.

## Seguridad y calidad
- No atribuir capacidades IA/autaprendizaje al importador hasta implementarlas.
- No asumir que compilar verde implica funcionar en el móvil.
- No almacenar secretos Firebase ni datos privados en el repositorio.
- Mantener compatibilidad con datos locales existentes y evitar borrarlos.
