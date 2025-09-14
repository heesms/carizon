import React, { useEffect, useState } from react
import { createRoot } from react-dom/client

function App(){
  const [data, setData] = useState(null)
  useEffect(()=>{
    fetch(/api/hello).then(r=>r.json()).then(setData).catch(console.error)
  },[])
  return (
    <div style={{fontFamily:"ui-sans-serif", padding:"24px"}}>
      <h1>Carizon</h1>
      <p>백엔드 연결 상태:</p>
      <pre>{JSON.stringify(data, null, 2)}</pre>
    </div>
  )
}
createRoot(document.getElementById("root")).render(<App/>)
