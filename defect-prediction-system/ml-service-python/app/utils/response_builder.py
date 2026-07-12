from typing import List, Dict, Any


def build_success_response(predictions: List[Dict[str, Any]]) -> Dict:
    return {"status": "success", "predictions": predictions}


def build_error_response(message: str) -> Dict:
    return {"status": "error", "message": message}
