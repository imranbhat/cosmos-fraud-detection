{{/*
Expand the name of the chart.
*/}}
{{- define "cosmos-fraud-detection.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "cosmos-fraud-detection.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "cosmos-fraud-detection.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "cosmos-fraud-detection.labels" -}}
helm.sh/chart: {{ include "cosmos-fraud-detection.chart" . }}
{{ include "cosmos-fraud-detection.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: cosmos-fraud-detection
{{- end }}

{{/*
Selector labels
*/}}
{{- define "cosmos-fraud-detection.selectorLabels" -}}
app.kubernetes.io/name: {{ include "cosmos-fraud-detection.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Service-specific labels
*/}}
{{- define "cosmos-fraud-detection.serviceLabels" -}}
{{- $root := index . 0 }}
{{- $service := index . 1 }}
helm.sh/chart: {{ include "cosmos-fraud-detection.chart" $root }}
app.kubernetes.io/name: {{ $service }}
app.kubernetes.io/instance: {{ $root.Release.Name }}
app.kubernetes.io/version: {{ $root.Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ $root.Release.Service }}
app.kubernetes.io/part-of: cosmos-fraud-detection
app.kubernetes.io/component: {{ $service }}
{{- end }}

{{/*
Service-specific selector labels
*/}}
{{- define "cosmos-fraud-detection.serviceSelectorLabels" -}}
{{- $root := index . 0 }}
{{- $service := index . 1 }}
app.kubernetes.io/name: {{ $service }}
app.kubernetes.io/instance: {{ $root.Release.Name }}
{{- end }}

{{/*
Create the image name for a service
*/}}
{{- define "cosmos-fraud-detection.image" -}}
{{- $root := index . 0 }}
{{- $imageValues := index . 1 }}
{{- printf "%s/%s:%s" $root.Values.global.image.registry $imageValues.repository $root.Values.global.image.tag }}
{{- end }}

{{/*
Create service account name
*/}}
{{- define "cosmos-fraud-detection.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "cosmos-fraud-detection.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}
