import React from 'react'
export default function AdSlot({id,className}:{id:string;className?:string}){
    return <div className={`border rounded bg-gray-100 text-gray-500 text-xs grid place-items-center ${className||''}`} style={{minHeight:80}}>AD: {id}</div>
}
