export async function fetchDemoList(q='') {
  return [
    {
      car_uid: 'GRAN-2023-50000-BLK-001',
      brand: 'HYUNDAI', model: 'GRANDEUR', trim: '2.5 GDi',
      year: 2023, mileage_km: 50000, color: 'BLACK',
      price: 35590000,
      prices: [{source:'ENC', price:35900000},{source:'KBCAR', price:35200000}],
    },
    {
      car_uid: 'K5-2022-42000-WHT-002',
      brand: 'KIA', model: 'K5', trim: '1.6T',
      year: 2022, mileage_km: 42000, color: 'WHITE',
      price: 22800000,
      prices: [{source:'KCAR', price:22900000},{source:'ENC', price:22600000}],
    }
  ].filter(x => !q || (x.model+x.trim).toLowerCase().includes(q.toLowerCase()))
}

export async function fetchDemoDetail(uid){
  const map = {
    'GRAN-2023-50000-BLK-001': {
      car_uid:'GRAN-2023-50000-BLK-001',
      brand:'HYUNDAI', model:'GRANDEUR', trim:'2.5 GDi',
      year:2023, mileage_km:50000, color:'BLACK', price:35590000,
      prices:[{source:'ENC',price:35900000},{source:'KBCAR',price:35200000}],
      sources:[
        {source:'ENC', url:'https://www.encar.com', mileage_km:50000, year:2023},
        {source:'KBCAR', url:'https://www.kbchachacha.com', mileage_km:48000, year:2023}
      ]
    },
    'K5-2022-42000-WHT-002': {
      car_uid:'K5-2022-42000-WHT-002',
      brand:'KIA', model:'K5', trim:'1.6T',
      year:2022, mileage_km:42000, color:'WHITE', price:22800000,
      prices:[{source:'KCAR',price:22900000},{source:'ENC',price:22600000}],
      sources:[
        {source:'KCAR', url:'https://www.kcar.com', mileage_km:42000, year:2022},
        {source:'ENC', url:'https://www.encar.com', mileage_km:43000, year:2022}
      ]
    }
  }
  return map[uid] ?? map['GRAN-2023-50000-BLK-001']
}