nextflow.enable.dsl=2

params.samples = ['sample_ctrl', 'sample_trt1', 'sample_trt2']
params.outdir = 'results'

process SIMULATE_MATRIX {
    tag "${sample_id}"
    cpus 1

    input:
    val sample_id

    output:
    tuple val(sample_id), path("${sample_id}_matrix.csv")

    script:
    """
    echo "barcode,feature,count" > ${sample_id}_matrix.csv
    for i in {1..200}; do
        echo "CELL\$((RANDOM % 10)),FEATURE\$((RANDOM % 20)),\$((RANDOM % 100))" >> ${sample_id}_matrix.csv
    done
    """
}

process EXTRACT_BARCODES {
    tag "${sample_id}"
    publishDir "${params.outdir}", mode: 'copy', saveAs: { filename -> "${sample_id}/${filename}" }

    input:
    tuple val(sample_id), path(matrix_csv)

    output:
    tuple val(sample_id), path("${sample_id}_barcodes.tsv")

    script:
    """
    awk -F',' 'NR>1 {print \$1}' ${matrix_csv} | sort | uniq -c | awk '{print \$2 "\t" \$1}' > ${sample_id}_barcodes.tsv
    """
}

process AGGREGATE_FEATURES {
    tag "${sample_id}"

    input:
    tuple val(sample_id), path(matrix_csv)

    output:
    tuple val(sample_id), path("${sample_id}_features.tsv")

    script:
    """
    awk -F',' 'NR>1 {print \$2, \$3}' ${matrix_csv} | sort | awk '{a[\$1]+=\$2} END {for (i in a) print i "\t" a[i]}' > ${sample_id}_features.tsv
    """
}

process COMPILE_SAMPLE_STATS {
    tag "${sample_id}"
    publishDir "${params.outdir}", mode: 'copy', saveAs: { filename -> "${sample_id}/${filename}" }

    input:
    tuple val(sample_id), path(barcodes), path(features)

    output:
    path "${sample_id}_summary.txt"

    script:
    """
    echo "=== ${sample_id} ===" > ${sample_id}_summary.txt
    echo "Top 3 Barcodes by Appearance:" >> ${sample_id}_summary.txt
    sort -k2,2nr ${barcodes} | head -n 3 >> ${sample_id}_summary.txt
    echo "" >> ${sample_id}_summary.txt
    echo "Top 3 Features by Count:" >> ${sample_id}_summary.txt
    sort -k2,2nr ${features} | head -n 3 >> ${sample_id}_summary.txt
    """
}

process GENERATE_RUN_REPORT {
    publishDir "${params.outdir}", mode: 'copy'

    input:
    path summaries

    output:
    path "run_report.txt"

    script:
    """
    echo "PIPELINE EXECUTION REPORT" > run_report.txt
    echo "=========================" >> run_report.txt
    date >> run_report.txt
    echo "" >> run_report.txt
    cat ${summaries} >> run_report.txt
    """
}

workflow {
    sample_ch = Channel.fromList(params.samples)
    matrix_ch = SIMULATE_MATRIX(sample_ch)

    barcode_ch = EXTRACT_BARCODES(matrix_ch)
    feature_ch = AGGREGATE_FEATURES(matrix_ch)

    merged_stats_ch = barcode_ch.join(feature_ch)
    summary_ch = COMPILE_SAMPLE_STATS(merged_stats_ch)

    GENERATE_RUN_REPORT(summary_ch.collect())
}