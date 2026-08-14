"""Domain layer: feature registry, validation, pipeline and evaluation.

Nothing here imports FastAPI or knows about HTTP. Only ``app.api`` is allowed
to depend on this package and translate its results into responses.
"""
