package com.github.frtu.samples.dataprocessing

import com.github.frtu.dataprocessing.framework.Transform
import com.github.frtu.samples.dataprocessing.model.input.Email
import com.github.frtu.samples.dataprocessing.model.output.EmailEntity

class EnrichmentTransform: Transform<Email, EmailEntity> {
}