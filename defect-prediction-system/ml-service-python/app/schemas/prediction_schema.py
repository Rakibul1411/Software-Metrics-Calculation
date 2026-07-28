from typing import Any, Dict, List, Literal, Optional

from pydantic import BaseModel, Field


class PredictionResultItem(BaseModel):
    class_name: str
    prediction: str


class PredictionResponse(BaseModel):
    status: str
    predictions: List[Dict[str, Any]]


class PredictionRequestOptions(BaseModel):
    classifier: Literal["knn", "svm"] = "knn"
    top_k: int = Field(default=3, ge=1)
    coral_option: bool = True
    label_column: str = "bug"


class ModelConfiguration(BaseModel):
    classifier: Literal["knn", "linear-svm"]
    selectedK: Optional[int] = None
    selectedC: Optional[float] = None
    decisionThreshold: float
    modelSelectionStrategy: str
    hyperparameterSelection: str = "automatic-source-only-validation"
    coralEnabled: bool
    coralType: str = "shallow/linear CORAL"
    imbalanceHandling: Optional[str] = None


class MetricAggregate(BaseModel):
    mean: Optional[float] = None
    std: Optional[float] = None
    validFoldCount: int = 0


class ModelSelection(BaseModel):
    strategy: str
    sourceProjectCount: int
    foldCount: int = 0
    selectedClassifier: Literal["knn", "svm"]
    selectedK: Optional[int] = None
    selectedC: Optional[float] = None
    decisionThreshold: float
    hyperparameterSelectionMetric: Optional[str] = None
    thresholdSelectionMetric: Optional[str] = None
    candidateValues: List[float | int] = Field(default_factory=list)
    candidateResults: List[Dict[str, Any]] = Field(default_factory=list)
    thresholdCandidateResults: List[Dict[str, Any]] = Field(default_factory=list)
    aggregateMetrics: Dict[str, MetricAggregate] = Field(default_factory=dict)
    foldResults: List[Dict[str, Any]] = Field(default_factory=list)
    warnings: List[str] = Field(default_factory=list)
