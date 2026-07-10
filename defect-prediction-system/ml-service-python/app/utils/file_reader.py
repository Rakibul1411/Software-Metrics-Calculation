import pandas as pd
from io import BytesIO
from fastapi import UploadFile


async def read_csv_from_upload(upload: UploadFile) -> pd.DataFrame:
    contents = await upload.read()
    return pd.read_csv(BytesIO(contents))
