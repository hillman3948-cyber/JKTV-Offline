package com.jktv.offline.remote

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun GuestStageQr(url:String,edge:Dp,modifier:Modifier=Modifier) {
    val matrix=remember(url){QrCodeMatrix.encodeUrl(url)}
    Column(modifier.background(Color(0xC70A1120),RoundedCornerShape(8.dp)).padding(6.dp),horizontalAlignment=Alignment.CenterHorizontally){
        Canvas(Modifier.size(edge).background(Color.White)){
            val cell=size.width/(matrix.size+8)
            matrix.forEachIndexed {y,row->row.forEachIndexed {x,on->if(on)drawRect(Color(0xFF050813),Offset((x+4)*cell,(y+4)*cell),Size(cell,cell))}}
        }
        Text("SCAN TO CONNECT",color=Color.White,fontSize=10.sp,modifier=Modifier.padding(top=4.dp),maxLines=1)
    }
}
