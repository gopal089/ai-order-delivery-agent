import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { Options } from './Options'
import '../styles/base.css'
import './options.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Options />
  </StrictMode>,
)
